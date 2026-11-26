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

async function request<T>(method: HttpMethod, path: string, options: RequestOptions = {}): Promise<T> {
  const { query, body, signal } = options;
  const headers: Record<string, string> = { Accept: 'application/json' };
  // TODO(ACC-07): CSRF 방식 확정 후 여기서 헤더 추가
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

  // 성공 204는 본문 없음
  if (res.status === 204 && res.ok) return null as T;

  // 본문이 없거나 JSON이 아님 (예: 지금 Spring Security 기본 401은 본문 없음)
  let parsed: unknown;
  try {
    parsed = text ? JSON.parse(text) : undefined;
  } catch {
    parsed = undefined;
  }
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
