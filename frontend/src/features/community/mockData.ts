// 커뮤니티 화면 가짜 데이터 (와이어프레임 D-01 예시). 가짜 데이터는 모두 이 파일에만 둔다.
// TODO(D): 서버 API가 생기면 communityApi.ts가 서버를 부르게 바꾸고 이 파일은 지운다.
//
// 미리보기에서 상태 바꿔 보기: 주소 끝에
//   ?mock=loading | empty | error   (목록 전체)
//   ?mock=more-error                (첫 10개는 오고, 다음 10개 불러오기만 실패)
//   (검색 조건 sort·q·cityId·days도 주소에 남으니 그 뒤에 &mock=... 로 붙여도 됨)
// 서버가 할 일(공개 게시물만, 정렬, 마지막 게시물 기준 이어 받기)을 이 파일이 흉내 낸다.
import type { PageResponse } from '../../shared/api/client'
import { POST_PAGE_SIZE, type CityOption, type PostListItem, type PostListQuery } from './api/communityApi.ts'

interface MockPost extends PostListItem {
  cityId: number
}

const CITIES = [
  { cityId: 1, name: '도쿄' },
  { cityId: 2, name: '오사카' },
  { cityId: 3, name: '부산' },
  { cityId: 4, name: '교토' },
  { cityId: 5, name: '제주' },
  { cityId: 6, name: '방콕' },
]

// D-01 카드 6개 + 이어 받기 확인용 18개 = 24개 (D-01 "검색 결과 24개")
const D01: Array<[string, number, number, string, number, string, string | null]> = [
  // 제목, 도시, 일수, 닉네임, 좋아요, 게시 시각, 수정 시각
  ['도쿄 3박 4일 가족여행 — 아이랑 걷기 좋은 코스', 1, 4, '지은', 12, '2026-11-25T10:00:00', '2026-11-25T21:30:00'],
  ['오사카 먹방 당일치기', 2, 1, '민수', 30, '2026-11-24T18:30:00', null],
  ['부산 바다 따라 2박 3일', 3, 3, '하늘', 8, '2026-11-24T09:10:00', null],
  ['교토 단풍 1박 2일', 4, 2, '유진', 5, '2026-11-23T20:00:00', '2026-11-26T14:05:00'],
  ['제주 동쪽 한 바퀴', 5, 3, '수아', 3, '2026-11-22T11:00:00', null],
  ['방콕 4박 5일 첫 해외여행', 6, 5, '도윤', 21, '2026-11-21T15:40:00', null],
]

function buildPosts(): MockPost[] {
  const posts: MockPost[] = D01.map(([title, cityId, days, nick, likes, published, updated], i) => ({
    postId: 100 - i,
    title,
    imageUrl: null,
    cityId,
    cityName: CITIES.find((c) => c.cityId === cityId)?.name ?? '',
    days,
    authorNickname: nick,
    likeCount: likes,
    publishedAt: published,
    updatedAt: updated,
  }))
  for (let i = 0; i < 18; i += 1) {
    const city = CITIES[i % CITIES.length]
    const day = String(20 - Math.floor(i / 2)).padStart(2, '0')
    posts.push({
      postId: 94 - i,
      title: `${city.name} 여행 코스 ${i + 1}`,
      imageUrl: null,
      cityId: city.cityId,
      cityName: city.name,
      days: (i % 7) + 1,
      authorNickname: i === 5 ? null : `여행자${i + 1}`, // 작성자 정보 없음 확인용
      likeCount: (i * 7) % 25,
      publishedAt: `2026-11-${day}T${i % 2 === 0 ? '13' : '08'}:00:00`,
      updatedAt: null,
    })
  }
  return posts
}

const mockPosts = buildPosts()

function mockMode(): string | null {
  return new URLSearchParams(window.location.search).get('mock')
}

function wait(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

// 정렬 (contracts): 최신순 = 처음 게시 시각 늦은 순, 좋아요순 = 좋아요 많은 순 → 늦게 게시한 순, 마지막은 postId 큰 순
function compare(sort: PostListQuery['sort']) {
  return (a: MockPost, b: MockPost) =>
    (sort === 'LIKES' ? b.likeCount - a.likeCount : 0) ||
    b.publishedAt.localeCompare(a.publishedAt) ||
    b.postId - a.postId
}

// PUB-03-01·02·03 공개 목록 (서버 흉내)
export async function mockGetPublicPosts(query: PostListQuery): Promise<PageResponse<PostListItem>> {
  const mode = mockMode()
  await wait(mode === 'loading' ? 600_000 : 500)
  if (mode === 'error' || (mode === 'more-error' && query.after !== undefined)) throw new Error('mock error')

  const keyword = query.q?.trim().toLowerCase() ?? ''
  const matched = (mode === 'empty' ? [] : mockPosts)
    .filter((p) => !keyword || p.title.toLowerCase().includes(keyword)) // 일부 일치, 대소문자 구분 안 함
    .filter((p) => query.cityId === undefined || p.cityId === query.cityId)
    .filter((p) => query.days === undefined || p.days === query.days)
    .sort(compare(query.sort))

  // 마지막 게시물 기준 이어 받기: 그 게시물 다음부터 (그 게시물이 없으면 처음부터)
  const lastIndex = query.after === undefined ? -1 : matched.findIndex((p) => p.postId === query.after)
  const start = lastIndex + 1
  const content = matched.slice(start, start + POST_PAGE_SIZE).map(({ cityId: _cityId, ...item }) => item)

  return {
    content,
    page: query.page ?? 0,
    size: POST_PAGE_SIZE,
    totalElements: matched.length,
    totalPages: Math.ceil(matched.length / POST_PAGE_SIZE),
    hasNext: start + POST_PAGE_SIZE < matched.length,
  }
}

// PUB-03-02 등록된 모든 도시 (가나다순)
export async function mockGetCityOptions(): Promise<CityOption[]> {
  await wait(200)
  return CITIES.map((c) => ({ cityId: c.cityId, name: c.name })).sort((a, b) => a.name.localeCompare(b.name, 'ko'))
}
