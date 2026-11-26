// 커뮤니티 서버 호출 (PUB-03 공개 목록·검색·정렬). 약속: contracts/community.yaml
// 지금은 가짜 데이터(mockData.ts)를 돌려준다. 화면 파일은 이 파일의 함수만 부른다.
// TODO(D): 서버 API가 생기고 A가 로그인 없이 열어 주면
//          1) 각 함수 안쪽을 shared/api/client.ts 호출로 바꾸고
//          2) mockData.ts를 지운다. (화면 파일은 고치지 않아도 됨)
import type { PageResponse } from '../../../shared/api/client'
import { mockGetCityOptions, mockGetPublicPosts } from '../mockData.ts'

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
