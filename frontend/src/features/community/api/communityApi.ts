// 커뮤니티 서버 호출 (PUB-01 공유·게시, PUB-02 비공개·수정, PUB-03 목록, PUB-04 상세, PUB-05 좋아요, PUB-06 북마크). 약속: contracts/community.yaml
// 지금은 가짜 데이터(mockData.ts)를 돌려준다. 화면 파일은 이 파일의 함수만 부른다.
// TODO(D): 서버 API가 생기고 A가 로그인 없이 열어 주면
//          1) 각 함수 안쪽을 shared/api/client.ts 호출로 바꾸고
//          2) mockData.ts를 지운다. (화면 파일은 고치지 않아도 됨)
import { ApiError, type PageResponse } from '../../../shared/api/client'
import {
  mockBookmarkPost,
  mockGetCityOptions,
  mockGetMyBookmarks,
  mockGetPostPreview,
  mockGetPublicPostDetail,
  mockGetPublicPosts,
  mockGetShareStatus,
  mockLikePost,
  mockMakePostPrivate,
  mockPublishPost,
  mockUpdatePostInfo,
  mockUnbookmarkPost,
  mockUnlikePost,
} from '../mockData.ts'

// 정렬: 최신순 / 좋아요순 (PUB-03-03)
export type PostSort = 'LATEST' | 'LIKES'

// ---- PUB-03-01 공개 목록 카드 한 개 (contracts PostListResponse) ----

export interface PostListItem {
  postId: number
  title: string // 공개용 제목 1~40자
  imageUrl: string | null // 대표 사진, null이면 기본 배경
  cityName: string
  days: number // 여행 일수 1~7
  authorNickname: string | null
  likeCount: number
  publishedAt: string // 처음 게시 시각
  updatedAt: string | null // 제목·소개·사진을 고친 시각, 고친 적 없으면 null
}

// 목록 조건 (PUB-03-02·03). 없는 값은 보내지 않음
export interface PostListQuery {
  sort: PostSort
  q?: string // 제목 검색어 0~50자
  cityId?: number
  days?: number // 1~7
  after?: number // 지금까지 받은 마지막 카드의 postId (다음 10개)
  page?: number // 몇 번째 요청인지(0부터)
}

export const POST_PAGE_SIZE = 10 // PUB-03-01 한 번에 10개

/** PUB-03-01 공개 여행 목록 (비회원 가능): GET /api/community/posts */
export function getPublicPosts(query: PostListQuery): Promise<PageResponse<PostListItem>> {
  return mockGetPublicPosts(query)
}

// ---- PUB-03-02 검색 조건 ----

export const SEARCH_KEYWORD_MAX = 50 // 제목 검색어 0~50자
export const SEARCH_DAYS_MAX = 7 // 일수 1~7일

export interface CityOption {
  cityId: number
  name: string
}

/**
 * PUB-03-02 도시 선택 목록: 서비스에 등록된 모든 도시.
 * TODO(B에 요청): 등록된 모든 도시를 돌려주는 API(CITY 전체 목록)가 아직 없다. 생기면 그 API를 부른다.
 */
export function getCityOptions(): Promise<CityOption[]> {
  return mockGetCityOptions()
}

// ---- PUB-04-01 공개 여행 상세 (contracts PostDetailResponse). 공개 허용 값만 들어 있다 ----

// 보는 사람 기준 표시 값 (PUB-04-03 버튼). 비회원이면 모두 false
export interface PostViewer {
  loggedIn: boolean
  mine: boolean // 내 게시물 → [좋아요]·[제목·소개 수정]·[비공개 전환]만
  liked: boolean // 채워진 [좋아요]
  bookmarked: boolean // 채워진 책갈피
  communityWritable: boolean // false(이용 제한 회원) → 버튼 모두 비활성
}

// 다음 장소까지 이동 (요금 없음). 임시 모양 — TODO(C와 맞추기, RTE-02-02)
export type SegmentMode = 'WALK' | 'TRANSIT' | 'CAR'
export interface PublicSegment {
  mode: SegmentMode
  durationMinutes: number | null // null이면 "이동시간 미확인"
  estimated: boolean // "(예상)"
  distanceMeters: number | null
  transitSummary: string | null // 노선·환승 요약
  walkSummary: string | null // 도보 구간 요약
}

export interface PublicPlace {
  order: number // 그날 방문 순서 (지도 핀 번호)
  name: string
  category: string | null
  startTime: string | null // "HH:mm" 여행 도시 현지 시각
  endTime: string | null
  stayMinutes: number | null
  lodging: boolean
  lat: number | null // null이면 지도 핀만 뺌
  lng: number | null
  segmentToNext: PublicSegment | null // 그날 마지막 장소면 null
}

export interface PublicDay {
  dayNo: number // N일차 (실제 날짜 없음)
  places: PublicPlace[]
}

export interface PostDetail {
  postId: number
  title: string
  intro: string | null // 0~100자, 줄바꿈 포함 가능
  imageUrl: string | null
  cityName: string
  mapProvider: 'KAKAO' | 'GOOGLE'
  days: number
  authorNickname: string | null
  likeCount: number
  publishedAt: string
  updatedAt: string | null
  viewer: PostViewer
  schedule: PublicDay[]
}

// 볼 수 없는 게시물 (비공개·관리자 숨김·원래 여행 휴지통·없는 게시물) 서버 에러 코드
export const POST_NOT_VISIBLE = 'PUBLIC_POST_NOT_VISIBLE'

/**
 * PUB-04-01 공개 여행 상세 (비회원 가능): GET /api/community/posts/{postId}
 * 볼 수 없으면 ApiError(code = PUBLIC_POST_NOT_VISIBLE, 404)
 */
export function getPublicPostDetail(postId: number): Promise<PostDetail> {
  return mockGetPublicPostDetail(postId)
}

// ---- PUB-05 좋아요·취소 (로그인 필요) ----

export interface LikeResult {
  liked: boolean
  likeCount: number
}

/** PUB-05-01 좋아요: PUT /api/community/posts/{postId}/like (이미 눌렀으면 그대로 성공) */
export function likePost(postId: number): Promise<LikeResult> {
  return mockLikePost(postId)
}

/** PUB-05-02 좋아요 취소: DELETE /api/community/posts/{postId}/like (없으면 그대로 성공) */
export function unlikePost(postId: number): Promise<LikeResult> {
  return mockUnlikePost(postId)
}

// ---- PUB-06 북마크 (로그인 필요) ----

export interface BookmarkResult {
  bookmarked: boolean
}

/** PUB-06-01 북마크 저장: PUT /api/community/posts/{postId}/bookmark (회원당 200개) */
export function bookmarkPost(postId: number): Promise<BookmarkResult> {
  return mockBookmarkPost(postId)
}

/** PUB-06-01 북마크 해제: DELETE /api/community/posts/{postId}/bookmark */
export function unbookmarkPost(postId: number): Promise<BookmarkResult> {
  return mockUnbookmarkPost(postId)
}

// PUB-06-02 내 북마크 카드 한 개. 볼 수 없게 된 게시물은 visible = false, post = null
export interface MyBookmarkItem {
  bookmarkId: number // 이어 받기 기준
  postId: number // 해제할 때 씀
  visible: boolean
  post: PostListItem | null
  bookmarkedAt: string
}

export const BOOKMARK_PAGE_SIZE = 20 // PUB-06-02 20개씩

/** PUB-06-02 내 북마크 목록: GET /api/community/bookmarks (담은 시각 최신순) */
export function getMyBookmarks(after?: number, page = 0): Promise<PageResponse<MyBookmarkItem>> {
  return mockGetMyBookmarks(after, page)
}

// ---- PUB-01 공유하기·미리보기·게시 (여행 소유자) ----

// 이 여행의 게시 상태: NONE 처음 / PUBLIC 공개 중(공유하기 비활성) / PRIVATE 비공개로 바꿈 / ADMIN_HIDDEN 관리자 비공개
export type ShareState = 'NONE' | 'PUBLIC' | 'PRIVATE' | 'ADMIN_HIDDEN'
export interface ShareStatus {
  status: ShareState
  postId: number | null
  title: string | null // PRIVATE이면 예전 공개용 제목 (입력 칸에 넣음)
  intro: string | null
}

/** PUB-01-01 공유하기 상태: GET /api/trips/{tripId}/post */
export function getShareStatus(tripId: number): Promise<ShareStatus> {
  return mockGetShareStatus(tripId)
}

// 공개 미리보기 (공개 허용 값만, 제목·소개는 화면이 들고 있음)
export interface PostPreview {
  cityName: string | null
  mapProvider: 'KAKAO' | 'GOOGLE' | null
  days: number
  authorNickname: string | null
  schedule: PublicDay[]
}

/** PUB-01-02 공개 미리보기: GET /api/trips/{tripId}/post-preview */
export function getPostPreview(tripId: number): Promise<PostPreview> {
  return mockGetPostPreview(tripId)
}

export interface PostInfoRequest {
  title: string // 1~40자
  intro: string | null // 0~100자
}

export interface PublishRequest extends PostInfoRequest {
  requestKey: string // 같은 요청 두 번 처리 방지 (게시 동작 1번마다 새로, 다시 시도할 때는 같은 값)
}

/** PUB-01-03 게시: POST /api/trips/{tripId}/post */
export function publishPost(tripId: number, req: PublishRequest): Promise<{ postId: number }> {
  return mockPublishPost(tripId, req)
}

// ---- PUB-02 내 게시물 (여행 소유자) ----

/** PUB-02-05 공개용 제목·소개 수정: PUT /api/community/posts/{postId}/info (고친 뒤의 상세) */
export function updatePostInfo(postId: number, req: PostInfoRequest): Promise<PostDetail> {
  return mockUpdatePostInfo(postId, req)
}

/** PUB-02-02 비공개 전환: PATCH /api/community/posts/{postId}/private */
export function makePostPrivate(postId: number): Promise<void> {
  return mockMakePostPrivate(postId)
}

/** 실패 안내 문구: 서버가 보낸 규칙 위반 문구(볼 수 없는 게시물·이용 제한·200개 초과 등)가 있으면 그 문구, 없으면 기능의 실패 문구 */
export function failMessage(e: unknown, fallback: string): string {
  return e instanceof ApiError && e.code !== null && e.message ? e.message : fallback
}
