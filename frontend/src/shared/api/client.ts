/*
 * 서버 호출 기본 설정 (AGENTS §7: 모든 서버 호출은 이 파일을 거친다).
 * - 서버 주소: VITE_API_BASE_URL
 * - 로그인: 서버 세션 쿠키 (credentials: 'include', JWT 쓰지 않음)
 * - 응답: ApiResponse {success, data, message} 중 data만 돌려주고, 실패는 ApiError로 던진다.
 * 타입은 contracts/common.yaml(ApiResponse.java·PageResponse.java)과 같다.
 * 화면 문구는 각 기능이 정한다(ApiError.message는 서버 문구 그대로, 없으면 '').
 */

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
}

export interface ErrorData {
  code: string;
  fieldErrors: FieldErrorData[];
}

export interface FieldErrorData {
  field: string;
  message: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number; // 0부터
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

/** 서버 호출 실패. status 0은 네트워크 실패 */
export class ApiError extends Error {
  status: number;
  code: string | null; // ErrorCode 이름 (예: 'INVALID_INPUT'), 본문이 없으면 null
  fieldErrors: FieldErrorData[];

  constructor(status: number, code: string | null, fieldErrors: FieldErrorData[], message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue | readonly QueryValue[]>;

interface RequestOptions {
  query?: QueryParams;
  body?: unknown;
  signal?: AbortSignal;
}

const baseUrl: string | undefined = import.meta.env.VITE_API_BASE_URL;

// TODO(ACC-07·ACC-02): 로그인 화면 이동(허용된 주소로만 복귀)은 pjh/security·pjh/login에서 등록
let unauthorizedHandler: ((error: ApiError) => void) | null = null;

/** 401 응답일 때 부를 함수 등록 (null이면 해제) */
export function setUnauthorizedHandler(handler: ((error: ApiError) => void) | null): void {
  unauthorizedHandler = handler;
}

function buildUrl(path: string, query?: QueryParams): string {
  if (!baseUrl) {
    throw new Error('서버 주소가 없어요. frontend/.env.local에 VITE_API_BASE_URL 확인');
  }
  const url = `${baseUrl.replace(/\/+$/, '')}${path}`;
  if (!query) return url;

  // undefined·null은 빼고, 배열은 같은 이름으로 반복
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    const values = Array.isArray(value) ? value : [value];
    for (const item of values) {
      if (item !== undefined && item !== null) params.append(key, String(item));
    }
  }
  const search = params.toString();
  return search ? `${url}?${search}` : url;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return isRecord(value) && typeof value.success === 'boolean';
}

function isErrorData(value: unknown): value is ErrorData {
  return isRecord(value) && typeof value.code === 'string' && Array.isArray(value.fieldErrors);
}

function isAbort(error: unknown, signal?: AbortSignal): boolean {
  return signal?.aborted === true || (error instanceof DOMException && error.name === 'AbortError');
}

// 실패를 던진다. 401이면 등록된 함수를 먼저 부른다
function fail(error: ApiError): never {
  if (error.status === 401 && unauthorizedHandler) {
    unauthorizedHandler(error);
  }
  throw error;
}

/*
 * CSRF (ACC-07): POST·PUT·PATCH·DELETE에 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 붙인다. GET에는 붙이지 않는다.
 * 쿠키가 없으면(첫 화면에서 바로 로그인, 로그인·로그아웃 뒤 쿠키가 지워진 경우) GET /api/auth/csrf로 한 번 발급받는다.
 * TODO(ACC-02): pjh/login에서 GET /api/auth/csrf(204)를 AuthController·contracts/auth.yaml에 추가. 그 전에도 /api/auth/** 응답(404 포함)에 XSRF-TOKEN 쿠키가 발급됨을 확인함(2026-11-26)
 * 화면과 API 도메인이 다르면 document.cookie로 읽을 수 없음 — 배포 구조 확정 시 /api/auth/csrf 응답 본문으로 토큰 받는 방식 검토 (AGENTS_A §2)
 */
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const CSRF_REJECTED_CODE = 'CSRF_TOKEN_INVALID';
const CSRF_METHODS: ReadonlySet<HttpMethod> = new Set<HttpMethod>(['POST', 'PUT', 'PATCH', 'DELETE']);

// 진행 중인 토큰 발급 요청 (동시에 여러 요청이 와도 한 번만 보낸다)
let csrfFetching: Promise<void> | null = null;

function readCsrfCookie(): string | null {
  for (const part of document.cookie.split(';')) {
    const [name, ...rest] = part.trim().split('=');
    if (name !== CSRF_COOKIE) continue;
    const raw = rest.join('=');
    if (!raw) return null;
    try {
      return decodeURIComponent(raw);
    } catch {
      return raw;
    }
  }
  return null;
}

// 토큰 쿠키 발급 요청. 응답 상태·본문은 보지 않고, 실패해도 ApiError를 던지지 않는다
function fetchCsrfCookie(): Promise<void> {
  if (!csrfFetching) {
    csrfFetching = fetch(buildUrl('/api/auth/csrf'), { method: 'GET', credentials: 'include' })
      .then(
        () => undefined,
        () => undefined,
      )
      .finally(() => {
        csrfFetching = null;
      });
  }
  return csrfFetching;
}

// 붙일 토큰. refresh면 쿠키가 있어도 다시 발급받고 읽는다
async function getCsrfToken(refresh: boolean): Promise<string | null> {
  const token = refresh ? null : readCsrfCookie();
  if (token) return token;
  await fetchCsrfCookie();
  return readCsrfCookie();
}

interface RawResponse {
  res: Response;
  parsed: unknown; // JSON 본문, 없거나 JSON이 아니면 undefined
}

async function send(
  method: HttpMethod,
  path: string,
  options: RequestOptions,
  csrfToken: string | null,
): Promise<RawResponse> {
  const { query, body, signal } = options;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (csrfToken) headers[CSRF_HEADER] = csrfToken;
  const init: RequestInit = { method, headers, credentials: 'include', signal };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  let res: Response;
  let text: string;
  try {
    res = await fetch(buildUrl(path, query), init);
    text = await res.text();
  } catch (error) {
    if (isAbort(error, signal)) throw error;
    if (!(error instanceof TypeError)) throw error; // 주소 설정 오류 등 개발자용 Error는 그대로
    return fail(new ApiError(0, null, [], ''));
  }

  let parsed: unknown;
  try {
    parsed = text ? JSON.parse(text) : undefined;
  } catch {
    parsed = undefined;
  }
  return { res, parsed };
}

function isCsrfRejected({ res, parsed }: RawResponse): boolean {
  return (
    res.status === 403 &&
    isApiResponse(parsed) &&
    isErrorData(parsed.data) &&
    parsed.data.code === CSRF_REJECTED_CODE
  );
}

async function request<T>(method: HttpMethod, path: string, options: RequestOptions = {}): Promise<T> {
  const needsCsrf = CSRF_METHODS.has(method);
  let raw = await send(method, path, options, needsCsrf ? await getCsrfToken(false) : null);
  if (needsCsrf && isCsrfRejected(raw)) {
    // 토큰을 다시 받아 한 번만 재시도한다.
    // CSRF 거부는 서버가 처리하기 전에 막은 것이라 재시도해도 두 번 처리되지 않는다
    raw = await send(method, path, options, await getCsrfToken(true));
  }
  const { res, parsed } = raw;

  // 성공 204는 본문 없음
  if (res.status === 204 && res.ok) return null as T;

  // 본문이 없거나 JSON이 아님
  if (!isApiResponse(parsed)) {
    return fail(new ApiError(res.status, null, [], ''));
  }

  if (!res.ok || !parsed.success) {
    const errorData = isErrorData(parsed.data) ? parsed.data : null;
    return fail(new ApiError(res.status, errorData?.code ?? null, errorData?.fieldErrors ?? [], parsed.message ?? ''));
  }

  return parsed.data as T;
}

/**
 * 서버 호출 함수. T는 ApiResponse의 data 타입.
 * 예: const trip = await api.get<TripDetailResponse>(`/api/trips/${tripId}`);
 */
export const api = {
  get: <T>(path: string, query?: QueryParams, signal?: AbortSignal) => request<T>('GET', path, { query, signal }),
  post: <T>(path: string, body?: unknown, signal?: AbortSignal) => request<T>('POST', path, { body, signal }),
  put: <T>(path: string, body?: unknown, signal?: AbortSignal) => request<T>('PUT', path, { body, signal }),
  patch: <T>(path: string, body?: unknown, signal?: AbortSignal) => request<T>('PATCH', path, { body, signal }),
  delete: <T>(path: string, body?: unknown, signal?: AbortSignal) => request<T>('DELETE', path, { body, signal }),
};
