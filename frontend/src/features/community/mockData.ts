// 커뮤니티 화면 가짜 데이터 (와이어프레임 D-01 예시). 가짜 데이터는 모두 이 파일에만 둔다.
// TODO(D): 서버 API가 생기면 communityApi.ts가 서버를 부르게 바꾸고 이 파일은 지운다.
//
// 미리보기에서 상태 바꿔 보기: 주소 끝에
//   ?mock=loading | empty | error   (목록 전체)
//   ?mock=more-error                (첫 10개는 오고, 다음 10개 불러오기만 실패)
//   (검색 조건 sort·q·cityId·days도 주소에 남으니 그 뒤에 &mock=... 로 붙여도 됨)
// 서버가 할 일(공개 게시물만, 정렬, 마지막 게시물 기준 이어 받기)을 이 파일이 흉내 낸다.
//
// 상세(/community/:postId) 상태 바꿔 보기: 주소 끝에
//   ?mock=loading | error | hidden  (hidden = 볼 수 없는 게시물)
//   ?viewer=guest | member | liked | mine | restricted   (보는 사람, 기본 guest = 비회원)
//   목록에 없는 번호(예: /community/1)도 볼 수 없는 게시물
//   ?mock=like-error      (좋아요·취소 실패 → 화면이 되돌림)
//   ?mock=bookmark-error  (북마크 저장·해제 실패)
//   ?mock=bookmark-limit  (북마크 200개 초과)
//   ?mock=gone            (그사이 볼 수 없게 된 게시물에 좋아요·북마크)
//
// 내 북마크(/mypage/bookmarks): ?mock=loading | empty | error | more-error
// 좋아요·북마크를 누른 결과는 이 파일에만 남는다. 새로고침하면 처음으로 돌아간다.
import { ApiError, type PageResponse } from '../../shared/api/client'
import {
  POST_NOT_VISIBLE,
  POST_PAGE_SIZE,
  type CityOption,
  BOOKMARK_PAGE_SIZE,
  type BookmarkResult,
  type LikeResult,
  type MyBookmarkItem,
  type PostDetail,
  type PostListItem,
  type PostListQuery,
  type PostViewer,
  type PublicDay,
} from './api/communityApi.ts'

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

// ---- PUB-04-01 공개 상세 ----

// 와이어프레임 D-02 도쿄 3박 4일 (1일차는 D-02 그대로, 2~4일차는 확인용)
const D02_SCHEDULE: PublicDay[] = [
  {
    dayNo: 1,
    places: [
      { order: 1, name: '센소지', category: '관광', startTime: '10:00', endTime: '11:30', stayMinutes: 90, lodging: false, lat: 35.7148, lng: 139.7967,
        segmentToNext: { mode: 'TRANSIT', durationMinutes: 15, estimated: true, distanceMeters: 3200, transitSummary: '지하철 긴자선 → 도보, 환승 0회', walkSummary: '도보 구간 6분 · 450m' } },
      { order: 2, name: '카페 A', category: '카페', startTime: '11:45', endTime: '12:30', stayMinutes: 45, lodging: false, lat: 35.7101, lng: 139.8107,
        segmentToNext: { mode: 'WALK', durationMinutes: 10, estimated: false, distanceMeters: 850, transitSummary: null, walkSummary: null } },
      { order: 3, name: '스카이트리', category: '전망대', startTime: '12:40', endTime: '14:40', stayMinutes: 120, lodging: false, lat: 35.7101, lng: 139.8107,
        segmentToNext: { mode: 'TRANSIT', durationMinutes: null, estimated: false, distanceMeters: null, transitSummary: null, walkSummary: null } },
      { order: 4, name: '우에노 공원', category: '공원', startTime: '15:00', endTime: '16:30', stayMinutes: 90, lodging: false, lat: 35.7156, lng: 139.7745,
        segmentToNext: { mode: 'TRANSIT', durationMinutes: 25, estimated: true, distanceMeters: 6100, transitSummary: 'JR 야마노테선 → 도보, 환승 0회', walkSummary: '도보 구간 8분 · 600m' } },
      { order: 5, name: '신주쿠 호텔', category: '숙소', startTime: '17:00', endTime: null, stayMinutes: null, lodging: true, lat: 35.6938, lng: 139.7034, segmentToNext: null },
    ],
  },
  {
    dayNo: 2,
    places: [
      { order: 1, name: '메이지 신궁', category: '관광', startTime: '09:30', endTime: '11:00', stayMinutes: 90, lodging: false, lat: 35.6764, lng: 139.6993,
        segmentToNext: { mode: 'CAR', durationMinutes: 20, estimated: true, distanceMeters: 5400, transitSummary: null, walkSummary: null } },
      { order: 2, name: '좌표 없는 골목 식당', category: '식당', startTime: '11:30', endTime: '12:30', stayMinutes: 60, lodging: false, lat: null, lng: null, segmentToNext: null },
    ],
  },
  { dayNo: 3, places: [] }, // 방문이 없는 날
  {
    dayNo: 4,
    places: [
      { order: 1, name: '츠키지 시장', category: '시장', startTime: '08:00', endTime: '09:30', stayMinutes: 90, lodging: false, lat: 35.6655, lng: 139.7707, segmentToNext: null },
    ],
  },
]

const VIEWERS: Record<string, PostViewer> = {
  guest: { loggedIn: false, mine: false, liked: false, bookmarked: false, communityWritable: false },
  member: { loggedIn: true, mine: false, liked: false, bookmarked: false, communityWritable: true },
  liked: { loggedIn: true, mine: false, liked: true, bookmarked: true, communityWritable: true },
  mine: { loggedIn: true, mine: true, liked: false, bookmarked: false, communityWritable: true },
  restricted: { loggedIn: true, mine: false, liked: false, bookmarked: false, communityWritable: false },
}

function currentViewer(): PostViewer {
  const viewerKey = new URLSearchParams(window.location.search).get('viewer') ?? 'guest'
  return { ...(VIEWERS[viewerKey] ?? VIEWERS.guest) }
}

function notVisible(): ApiError {
  return new ApiError(404, POST_NOT_VISIBLE, [], '볼 수 없는 게시물이에요.')
}

// PUB-04-01 공개 상세 (서버 흉내): 목록에 있는 게시물만, 공개 허용 값만
export async function mockGetPublicPostDetail(postId: number): Promise<PostDetail> {
  const mode = mockMode()
  await wait(mode === 'loading' ? 600_000 : 500)
  if (mode === 'error') throw new Error('mock error')
  const post = mockPosts.find((p) => p.postId === postId)
  if (!post || mode === 'hidden') throw notVisible()

  const viewer = currentViewer()
  viewer.liked = viewer.loggedIn && myLikes.has(postId)
  viewer.bookmarked = viewer.loggedIn && myBookmarks.some((b) => b.postId === postId)
  const isD02 = post.postId === 100
  return {
    postId: post.postId,
    title: post.title,
    intro: isD02 ? '아이와 함께 걷기 좋은 코스로 짰어요. 오전엔 관광, 오후엔 공원 위주예요.' : null,
    imageUrl: post.imageUrl,
    cityName: post.cityName,
    mapProvider: post.cityName === '부산' || post.cityName === '제주' ? 'KAKAO' : 'GOOGLE',
    days: post.days,
    authorNickname: post.authorNickname,
    likeCount: post.likeCount,
    publishedAt: post.publishedAt,
    updatedAt: post.updatedAt,
    viewer,
    // D-02 예시가 아니면 일수만큼 빈 날 (일정 확인은 /community/100에서)
    schedule: isD02 ? D02_SCHEDULE : Array.from({ length: post.days }, (_, i) => ({ dayNo: i + 1, places: [] })),
  }
}

// ---- PUB-05 좋아요·PUB-06 북마크 (가짜 저장) ----

// ?viewer=liked 이면 처음부터 100번(D-02 도쿄)에 좋아요·북마크 한 상태
const myLikes = new Set<number>(currentViewer().liked ? [100] : [])

// 내 북마크: D-09 예시(오사카·교토·볼 수 없는 게시물) + 이어 받기 확인용 22개
interface MockBookmark {
  bookmarkId: number
  postId: number
  bookmarkedAt: string
}
const HIDDEN_POST_ID = 1 // 목록에 없는 번호 = 볼 수 없게 된 게시물
let nextBookmarkId = 1000
const myBookmarks: MockBookmark[] = [
  ...(currentViewer().bookmarked ? [{ bookmarkId: 999, postId: 100, bookmarkedAt: '2026-11-26T09:00:00' }] : []),
  { bookmarkId: 998, postId: 99, bookmarkedAt: '2026-11-25T20:00:00' }, // 오사카 먹방 당일치기
  { bookmarkId: 997, postId: 97, bookmarkedAt: '2026-11-25T19:00:00' }, // 교토 단풍 1박 2일
  { bookmarkId: 996, postId: HIDDEN_POST_ID, bookmarkedAt: '2026-11-25T18:00:00' }, // 볼 수 없는 게시물
  ...Array.from({ length: 22 }, (_, i) => ({
    bookmarkId: 995 - i,
    postId: 94 - (i % 18),
    bookmarkedAt: `2026-11-${String(24 - Math.floor(i / 2)).padStart(2, '0')}T${i % 2 === 0 ? '12' : '08'}:00:00`,
  })),
]

function failIf(mode: string) {
  if (mockMode() === mode) throw new Error('mock error')
  if (mockMode() === 'gone') throw notVisible()
}

export async function mockLikePost(postId: number): Promise<LikeResult> {
  await wait(400)
  failIf('like-error')
  const post = mockPosts.find((p) => p.postId === postId)
  if (!post) throw notVisible()
  if (!myLikes.has(postId)) {
    myLikes.add(postId)
    post.likeCount += 1
  }
  return { liked: true, likeCount: post.likeCount }
}

export async function mockUnlikePost(postId: number): Promise<LikeResult> {
  await wait(400)
  failIf('like-error')
  const post = mockPosts.find((p) => p.postId === postId)
  if (!post) throw notVisible()
  if (myLikes.delete(postId)) post.likeCount = Math.max(0, post.likeCount - 1)
  return { liked: false, likeCount: post.likeCount }
}

export async function mockBookmarkPost(postId: number): Promise<BookmarkResult> {
  await wait(400)
  failIf('bookmark-error')
  if (!mockPosts.some((p) => p.postId === postId)) throw notVisible()
  if (myBookmarks.some((b) => b.postId === postId)) return { bookmarked: true }
  if (mockMode() === 'bookmark-limit' || myBookmarks.length >= 200) {
    throw new ApiError(409, 'BOOKMARK_LIMIT_EXCEEDED', [], '북마크는 최대 200개까지 담을 수 있어요.')
  }
  myBookmarks.unshift({ bookmarkId: nextBookmarkId++, postId, bookmarkedAt: '2026-11-26T15:00:00' })
  return { bookmarked: true }
}

export async function mockUnbookmarkPost(postId: number): Promise<BookmarkResult> {
  await wait(400)
  if (mockMode() === 'bookmark-error') throw new Error('mock error')
  const index = myBookmarks.findIndex((b) => b.postId === postId)
  if (index >= 0) myBookmarks.splice(index, 1)
  return { bookmarked: false }
}

// PUB-06-02 내 북마크 (서버 흉내: 담은 시각 최신순, 마지막 북마크 다음부터, 볼 수 없으면 내용 비움)
export async function mockGetMyBookmarks(after: number | undefined, page: number): Promise<PageResponse<MyBookmarkItem>> {
  const mode = mockMode()
  await wait(mode === 'loading' ? 600_000 : 500)
  if (mode === 'error' || (mode === 'more-error' && after !== undefined)) throw new Error('mock error')
  const all = mode === 'empty' ? [] : myBookmarks
  const start = after === undefined ? 0 : all.findIndex((b) => b.bookmarkId === after) + 1
  const content = all.slice(start, start + BOOKMARK_PAGE_SIZE).map((b) => {
    const found = mockPosts.find((p) => p.postId === b.postId)
    const post = found ? (({ cityId: _cityId, ...item }) => item)(found) : null
    return { bookmarkId: b.bookmarkId, postId: b.postId, visible: post !== null, post, bookmarkedAt: b.bookmarkedAt }
  })
  return {
    content,
    page,
    size: BOOKMARK_PAGE_SIZE,
    totalElements: all.length,
    totalPages: Math.ceil(all.length / BOOKMARK_PAGE_SIZE),
    hasNext: start + BOOKMARK_PAGE_SIZE < all.length,
  }
}
