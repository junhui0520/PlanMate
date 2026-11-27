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
//   ?mock=edit-error | private-error   (내 게시물 제목·소개 수정 / 비공개 전환 실패, ?viewer=mine 과 함께)
//
// 공유하기(/trips/:tripId/share):
//   ?share=none | private | public | admin   (이 여행의 게시 상태, 기본 none = 처음 공유)
//   ?mock=not-owner | restricted             (소유자 아님 / 이용 제한 → 창을 열지 않고 안내)
//   ?mock=preview-error | no-schedule        (미리보기 실패 / 일정 없음)
//   ?mock=publish-error | already-public     (게시 실패 / 그사이 이미 공개)
//
// 내 북마크(/mypage/bookmarks): ?mock=loading | empty | error | more-error
// 댓글(상세 아래): 100번은 D-02 예시 3개, 99번은 이어 받기 확인용 25개
//   ?mock=comments-loading | comments-error | comments-more-error | comment-error | comment-delete-error
//   ?viewer=member(내 댓글 = 지은) | restricted(쓰기·고치기·지우기 거절) | 없으면 비회원
// 복제(상세 [내 여행으로 복제], ?viewer=member):
//   ?mock=clone-limit (이미 3개) | clone-date (시작일 범위 밖) | clone-error (복제 실패) | restricted 회원은 ?viewer=restricted
// 신고(상세 [신고]·남의 댓글 [···] > [신고], ?viewer=member):
//   같은 대상을 두 번 신고하면 "이미 접수된 신고예요." / ?mock=report-error (신고 실패) | report-gone (그사이 지워짐)
// 내 게시글(/mypage/posts): ?mock=loading | empty | error | more-error | private-error, ?viewer=restricted(버튼 비활성)
//   공개 = 도쿄 예시(100), 비공개 = 부산 겨울 바다(여행 2 → [공개 전환]하면 예전 제목이 들어 있음), 관리자 비공개 = 제주 맛집 지도
// 좋아요·북마크를 누른 결과는 이 파일에만 남는다. 새로고침하면 처음으로 돌아간다.
import { ApiError, type PageResponse } from '../../shared/api/client'
import {
  POST_NOT_VISIBLE,
  POST_PAGE_SIZE,
  type CityOption,
  BOOKMARK_PAGE_SIZE,
  type BookmarkResult,
  type CloneCheck,
  type CloneRequest,
  type CommentItem,
  type LikeResult,
  type MyBookmarkItem,
  type MyPostItem,
  type PostDetail,
  type PostInfoRequest,
  type PostListItem,
  type PostPreview,
  type PublishRequest,
  type ReportRequest,
  type ShareStatus,
  type PostListQuery,
  type PostViewer,
  type PublicDay,
} from './api/communityApi.ts'

interface MockPost extends PostListItem {
  cityId: number
  intro: string | null
  hidden?: boolean // 비공개로 바꿈(PUB-02-02) → 목록·상세·북마크에서 볼 수 없음
  adminHidden?: boolean // 관리자 비공개 (hidden도 true)
}

// 공개 게시물로 보이는 것만 (비공개로 바꾼 게시물 제외)
function visiblePost(postId: number): MockPost | undefined {
  return mockPosts.find((p) => p.postId === postId && !p.hidden)
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
    intro: i === 0 ? '아이와 함께 걷기 좋은 코스로 짰어요. 오전엔 관광, 오후엔 공원 위주예요.' : null,
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
      intro: null,
      likeCount: (i * 7) % 25,
      publishedAt: `2026-11-${day}T${i % 2 === 0 ? '13' : '08'}:00:00`,
      updatedAt: null,
    })
  }
  return posts
}

const mockPosts = buildPosts()

// 서버 카드 모양(PostListItem)만 남김
function toListItem(p: MockPost): PostListItem {
  return {
    postId: p.postId,
    title: p.title,
    imageUrl: p.imageUrl,
    cityName: p.cityName,
    days: p.days,
    authorNickname: p.authorNickname,
    likeCount: p.likeCount,
    publishedAt: p.publishedAt,
    updatedAt: p.updatedAt,
  }
}

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
  const matched = (mode === 'empty' ? [] : mockPosts.filter((p) => !p.hidden))
    .filter((p) => !keyword || p.title.toLowerCase().includes(keyword)) // 일부 일치, 대소문자 구분 안 함
    .filter((p) => query.cityId === undefined || p.cityId === query.cityId)
    .filter((p) => query.days === undefined || p.days === query.days)
    .sort(compare(query.sort))

  // 마지막 게시물 기준 이어 받기: 그 게시물 다음부터 (그 게시물이 없으면 처음부터)
  const lastIndex = query.after === undefined ? -1 : matched.findIndex((p) => p.postId === query.after)
  const start = lastIndex + 1
  const content = matched.slice(start, start + POST_PAGE_SIZE).map(toListItem)

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
  const post = visiblePost(postId)
  if (!post || mode === 'hidden') throw notVisible()

  const viewer = currentViewer()
  // 방금 게시한 내 게시물은 ?viewer 없이도 내 게시물로 보임
  if (!new URLSearchParams(window.location.search).has('viewer') && myPostIds.has(postId)) Object.assign(viewer, VIEWERS.mine)
  viewer.liked = viewer.loggedIn && myLikes.has(postId)
  viewer.bookmarked = viewer.loggedIn && myBookmarks.some((b) => b.postId === postId)
  const isD02 = post.postId === 100 || myPostIds.has(postId) // 게시한 게시물도 D-02 일정으로 보여 줌
  return {
    postId: post.postId,
    title: post.title,
    intro: post.intro,
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
  const post = visiblePost(postId)
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
  if (!visiblePost(postId)) throw notVisible()
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
    const found = visiblePost(b.postId)
    const post = found ? toListItem(found) : null
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

// ---- PUB-01 공유하기·미리보기·게시, PUB-02-02 비공개 전환, PUB-02-05 제목·소개 수정 ----

const myPostIds = new Set<number>() // 이 화면에서 게시한 내 게시물
const sharedPosts = new Map<number, number>() // 여행 ID → 게시물 ID
let nextPostId = 200

// ?share= 로 이 여행의 처음 게시 상태를 정함 (private·public·admin이면 도쿄 예시 게시물 100번을 이 여행 게시물로 봄)
function shareOf(tripId: number): { post: MockPost | undefined; adminHidden: boolean } {
  const param = new URLSearchParams(window.location.search).get('share')
  if (!sharedPosts.has(tripId) && (param === 'private' || param === 'public' || param === 'admin')) {
    sharedPosts.set(tripId, 100)
    const post = mockPosts.find((p) => p.postId === 100)
    if (post) post.hidden = param !== 'public'
  }
  const postId = sharedPosts.get(tripId)
  const post = mockPosts.find((p) => p.postId === postId)
  return { post, adminHidden: param === 'admin' || Boolean(post?.adminHidden) }
}

function rejectIfNotAllowed() {
  const mode = mockMode()
  if (mode === 'not-owner') throw new ApiError(403, 'POST_OWNER_ONLY', [], '여행 소유자만 공개할 수 있어요.')
  if (mode === 'restricted') throw new ApiError(403, 'POST_RESTRICTED', [], '이용 제한 중에는 게시할 수 없어요.')
}

export async function mockGetShareStatus(tripId: number): Promise<ShareStatus> {
  await wait(300)
  if (mockMode() === 'not-owner') rejectIfNotAllowed()
  const { post, adminHidden } = shareOf(tripId)
  if (!post) return { status: 'NONE', postId: null, title: null, intro: null }
  const status = adminHidden ? 'ADMIN_HIDDEN' : post.hidden ? 'PRIVATE' : 'PUBLIC'
  return { status, postId: post.postId, title: post.title, intro: post.intro }
}

// 미리보기: 원래 여행(도쿄 3박 4일)의 공개 허용 값만
export async function mockGetPostPreview(_tripId: number): Promise<PostPreview> {
  await wait(mockMode() === 'loading' ? 600_000 : 500)
  rejectIfNotAllowed()
  if (mockMode() === 'preview-error') throw new Error('mock error')
  if (mockMode() === 'no-schedule') throw new ApiError(400, 'POST_NO_SCHEDULE', [], '일정을 추가한 뒤 공유할 수 있어요.')
  return { cityName: '도쿄', mapProvider: 'GOOGLE', days: 4, authorNickname: '지은', schedule: D02_SCHEDULE }
}

const usedRequestKeys = new Map<string, number>() // 같은 요청 두 번 처리 방지 흉내

export async function mockPublishPost(tripId: number, req: PublishRequest): Promise<{ postId: number }> {
  await wait(700)
  const done = usedRequestKeys.get(req.requestKey)
  if (done !== undefined) return { postId: done }
  rejectIfNotAllowed()
  if (mockMode() === 'publish-error') throw new Error('mock error')
  const { post, adminHidden } = shareOf(tripId)
  if (adminHidden) throw new ApiError(403, 'POST_ADMIN_HIDDEN', [], '관리자가 비공개로 바꾼 게시물이라 다시 공유할 수 없어요.')
  if (mockMode() === 'already-public' || (post && !post.hidden)) {
    throw new ApiError(409, 'POST_ALREADY_PUBLIC', [], '이미 공개한 여행이에요.')
  }
  let postId: number
  if (post) {
    // 비공개로 바꾼 게시물을 다시 공개 (좋아요·처음 게시 시각 그대로, 수정 시각 기록)
    Object.assign(post, { hidden: false, title: req.title, intro: req.intro, updatedAt: '2026-11-27T15:00:00' })
    postId = post.postId
  } else {
    postId = nextPostId++
    mockPosts.unshift({
      postId, cityId: 1, cityName: '도쿄', days: 4, title: req.title, intro: req.intro, imageUrl: null,
      authorNickname: '지은', likeCount: 0, publishedAt: '2026-11-27T15:00:00', updatedAt: null,
    })
    sharedPosts.set(tripId, postId)
  }
  myPostIds.add(postId)
  usedRequestKeys.set(req.requestKey, postId)
  return { postId }
}

export async function mockUpdatePostInfo(postId: number, req: PostInfoRequest): Promise<PostDetail> {
  await wait(500)
  if (mockMode() === 'edit-error') throw new Error('mock error')
  const post = visiblePost(postId)
  if (!post) throw notVisible()
  Object.assign(post, { title: req.title, intro: req.intro, updatedAt: '2026-11-27T15:10:00' })
  myPostIds.add(postId)
  return mockGetPublicPostDetail(postId)
}

export async function mockMakePostPrivate(postId: number): Promise<void> {
  await wait(500)
  if (mockMode() === 'private-error') throw new Error('mock error')
  const post = mockPosts.find((p) => p.postId === postId)
  if (!post) throw notVisible()
  post.hidden = true
}

// ---- PUB-02-04 내 게시글 ----

// D-10 예시: 비공개로 바꾼 게시물·관리자 비공개 게시물 (공개 목록에는 안 보임)
mockPosts.push(
  { postId: 901, cityId: 3, cityName: '부산', days: 2, title: '부산 겨울 바다 1박 2일', intro: '광안리 야경 위주예요.',
    imageUrl: null, authorNickname: '지은', likeCount: 12, publishedAt: '2026-10-20T10:00:00', updatedAt: '2026-10-28T09:00:00', hidden: true },
  { postId: 902, cityId: 5, cityName: '제주', days: 3, title: '제주 맛집 지도', intro: null,
    imageUrl: null, authorNickname: '지은', likeCount: 12, publishedAt: '2026-10-20T09:00:00', updatedAt: '2026-10-28T08:00:00',
    hidden: true, adminHidden: true },
)
sharedPosts.set(2, 901) // 여행 2 = 비공개로 바꾼 부산 게시물
sharedPosts.set(3, 902) // 여행 3 = 관리자 비공개 제주 게시물

// 내 게시물: 도쿄(100) + 부산(901) + 제주(902) + 이어 받기 확인용 10개
const MY_POSTS: Array<{ postId: number; tripId: number; comments: number }> = [
  { postId: 100, tripId: 1, comments: 3 },
  { postId: 901, tripId: 2, comments: 3 },
  { postId: 902, tripId: 3, comments: 3 },
  ...Array.from({ length: 10 }, (_, i) => ({ postId: 94 - i, tripId: 1094 - i, comments: i % 3 })),
]
const MY_POSTS_PAGE_SIZE = 10

export async function mockGetMyPosts(after: number | undefined, page: number): Promise<PageResponse<MyPostItem>> {
  const mode = mockMode()
  await wait(mode === 'loading' ? 600_000 : 500)
  if (mode === 'error' || (mode === 'more-error' && after !== undefined)) throw new Error('mock error')
  const writable = currentViewer().communityWritable || !new URLSearchParams(window.location.search).has('viewer')
  const all = mode === 'empty' ? [] : [...myPostIds].filter((id) => !MY_POSTS.some((m) => m.postId === id))
    .map((postId) => ({ postId, tripId: [...sharedPosts].find(([, p]) => p === postId)?.[0] ?? 1, comments: 0 }))
    .concat(MY_POSTS)
  const start = after === undefined ? 0 : all.findIndex((m) => m.postId === after) + 1
  const content: MyPostItem[] = all.slice(start, start + MY_POSTS_PAGE_SIZE).flatMap((m) => {
    const p = mockPosts.find((x) => x.postId === m.postId)
    if (!p) return []
    return [{
      postId: p.postId,
      tripId: m.tripId,
      title: p.title,
      status: p.adminHidden ? 'ADMIN_HIDDEN' : p.hidden ? 'PRIVATE' : 'PUBLIC',
      publishedAt: p.publishedAt,
      updatedAt: p.updatedAt,
      likeCount: p.likeCount,
      commentCount: m.comments,
      communityWritable: writable,
    }]
  })
  return {
    content,
    page,
    size: MY_POSTS_PAGE_SIZE,
    totalElements: all.length,
    totalPages: Math.ceil(all.length / MY_POSTS_PAGE_SIZE),
    hasNext: start + MY_POSTS_PAGE_SIZE < all.length,
  }
}

// ---- PUB-08 공개 댓글 ----

interface MockComment {
  commentId: number
  postId: number
  authorId: number
  nickname: string
  content: string
  createdAt: string
  edited: boolean
}

const ME_ID = 1 // 지은 (?viewer=member·mine·liked일 때 나)
let nextCommentId = 5000
let lastWrittenAt = 0 // 10초 제한 흉내

const mockComments: MockComment[] = [
  // D-02 예시
  { commentId: 1, postId: 100, authorId: 2, nickname: '민수', content: '저도 이 코스로 다녀왔어요! 스카이트리는 해 질 때 추천해요.', createdAt: '2026-10-28T19:00:00', edited: false },
  { commentId: 2, postId: 100, authorId: 3, nickname: '하늘', content: '카페 A 웨이팅은 얼마나 됐나요?', createdAt: '2026-11-27T09:10:00', edited: false },
  { commentId: 3, postId: 100, authorId: ME_ID, nickname: '지은', content: '30분 정도 기다렸어요.', createdAt: '2026-11-27T14:05:00', edited: true },
  // 이어 받기 확인용 (99번 게시물 25개, 4·5번째는 HTML 글자 그대로·줄바꿈 확인)
  ...Array.from({ length: 25 }, (_, i) => ({
    commentId: 100 + i,
    postId: 99,
    authorId: i % 4 === 0 ? ME_ID : 2,
    nickname: i % 4 === 0 ? '지은' : '민수',
    content: i === 3 ? '<b>굵게?</b> <script>alert(1)</script>' : i === 4 ? '첫 줄\n둘째 줄' : `댓글 ${i + 1}`,
    createdAt: `2026-11-${String(1 + Math.floor(i / 2)).padStart(2, '0')}T${i % 2 === 0 ? '10' : '15'}:00:00`,
    edited: false,
  })),
]

function commentViewerId(): number | null {
  const v = currentViewer()
  return v.loggedIn ? ME_ID : null
}

function toCommentItem(c: MockComment): CommentItem {
  return {
    commentId: c.commentId,
    content: c.content,
    authorNickname: c.nickname,
    createdAt: c.createdAt,
    edited: c.edited,
    mine: commentViewerId() === c.authorId,
  }
}

export async function mockGetComments(postId: number, after: number | undefined, page: number): Promise<PageResponse<CommentItem>> {
  const mode = mockMode()
  await wait(mode === 'comments-loading' ? 600_000 : 400)
  if (mode === 'comments-error' || (mode === 'comments-more-error' && after !== undefined)) throw new Error('mock error')
  if (!visiblePost(postId)) throw notVisible()
  const all = mockComments.filter((c) => c.postId === postId)
  const start = after === undefined ? 0 : all.findIndex((c) => c.commentId === after) + 1
  return {
    content: all.slice(start, start + COMMENT_PAGE).map(toCommentItem),
    page,
    size: COMMENT_PAGE,
    totalElements: all.length,
    totalPages: Math.ceil(all.length / COMMENT_PAGE),
    hasNext: start + COMMENT_PAGE < all.length,
  }
}
const COMMENT_PAGE = 20

function rejectRestricted(code: string, message: string) {
  if (new URLSearchParams(window.location.search).get('viewer') === 'restricted') throw new ApiError(403, code, [], message)
}

export async function mockWriteComment(postId: number, content: string): Promise<CommentItem> {
  await wait(500)
  rejectRestricted('COMMENT_RESTRICTED', '이용 제한 중에는 댓글을 쓸 수 없어요.')
  if (mockMode() === 'comment-error') throw new Error('mock error')
  if (!visiblePost(postId)) throw notVisible()
  if (Date.now() - lastWrittenAt < 10_000) throw new ApiError(429, 'COMMENT_TOO_FAST', [], '잠시 후 다시 등록해 주세요.')
  lastWrittenAt = Date.now()
  const c: MockComment = {
    commentId: nextCommentId++, postId, authorId: ME_ID, nickname: '지은', content: content.trim(),
    createdAt: '2026-11-27T15:30:00', edited: false,
  }
  mockComments.push(c)
  return toCommentItem(c)
}

export async function mockEditComment(commentId: number, content: string): Promise<CommentItem> {
  await wait(500)
  rejectRestricted('COMMENT_EDIT_RESTRICTED', '이용 제한 중에는 댓글을 고칠 수 없어요.')
  if (mockMode() === 'comment-error') throw new Error('mock error')
  const c = mockComments.find((x) => x.commentId === commentId)
  if (!c) throw new ApiError(404, 'RESOURCE_NOT_ACCESSIBLE', [], '이미 삭제되었거나 볼 수 없는 내용이에요.')
  if (c.authorId !== commentViewerId()) throw new ApiError(403, 'COMMENT_EDIT_FORBIDDEN', [], '내가 쓴 댓글만 고칠 수 있어요.')
  Object.assign(c, { content: content.trim(), edited: true })
  return toCommentItem(c)
}

export async function mockDeleteComment(commentId: number): Promise<void> {
  await wait(500)
  rejectRestricted('COMMUNITY_FEATURE_RESTRICTED', '이용 제한 중에는 커뮤니티 기능을 쓸 수 없어요.')
  if (mockMode() === 'comment-delete-error') throw new Error('mock error')
  const index = mockComments.findIndex((x) => x.commentId === commentId)
  if (index < 0) throw new ApiError(404, 'RESOURCE_NOT_ACCESSIBLE', [], '이미 삭제되었거나 볼 수 없는 내용이에요.')
  if (mockComments[index].authorId !== commentViewerId()) throw new ApiError(403, 'COMMENT_DELETE_FORBIDDEN', [], '내가 쓴 댓글만 지울 수 있어요.')
  mockComments.splice(index, 1)
}

// ---- PUB-07 내 여행으로 복제 ----

const LIMIT_MESSAGE = '참여 중인 여행을 포함해 여행은 최대 3개까지 가질 수 있어요. 기존 여행을 삭제하거나 나가면 더 만들 수 있어요.'
const cloneKeys = new Map<string, number>() // 같은 요청 두 번 처리 방지 흉내
let nextTripId = 300

export async function mockCheckClonable(postId: number): Promise<CloneCheck> {
  await wait(300)
  rejectRestricted('CLONE_RESTRICTED', '이용 제한 중에는 복제할 수 없어요.')
  if (!visiblePost(postId)) throw notVisible()
  if (mockMode() === 'clone-limit') throw new ApiError(409, 'TRIP_LIMIT_EXCEEDED', [], LIMIT_MESSAGE)
  return { activeTripCount: 2, maxTripCount: 3 }
}

export async function mockCloneToMyTrip(postId: number, req: CloneRequest): Promise<{ tripId: number }> {
  await wait(700)
  const done = cloneKeys.get(req.requestKey)
  if (done !== undefined) return { tripId: done }
  rejectRestricted('CLONE_RESTRICTED', '이용 제한 중에는 복제할 수 없어요.')
  if (!visiblePost(postId)) throw notVisible()
  if (mockMode() === 'clone-error') throw new Error('mock error')
  if (mockMode() === 'clone-date') {
    throw new ApiError(400, 'TRIP_START_DATE_OUT_OF_RANGE', [], '시작일은 오늘부터 1년 후까지 고를 수 있어요.')
  }
  const tripId = nextTripId++
  cloneKeys.set(req.requestKey, tripId)
  return { tripId }
}

// ---- PUB-09-01 신고 접수 ----

const receivedReports = new Set<string>() // 처리 전(접수) 신고: "POST:100" / "COMMENT:2"
let nextReportId = 700

async function mockReport(key: string, exists: boolean): Promise<{ reportId: number }> {
  await wait(500)
  rejectRestricted('COMMUNITY_FEATURE_RESTRICTED', '이용 제한 중에는 커뮤니티 기능을 쓸 수 없어요.')
  if (mockMode() === 'report-error') throw new Error('mock error')
  if (!exists || mockMode() === 'report-gone') {
    throw new ApiError(404, 'RESOURCE_NOT_ACCESSIBLE', [], '이미 삭제되었거나 볼 수 없는 내용이에요.')
  }
  if (receivedReports.has(key)) throw new ApiError(409, 'REPORT_DUPLICATE', [], '이미 접수된 신고예요.')
  receivedReports.add(key)
  return { reportId: nextReportId++ }
}

export function mockReportPost(postId: number, _req: ReportRequest): Promise<{ reportId: number }> {
  return mockReport(`POST:${postId}`, Boolean(visiblePost(postId)))
}

export function mockReportComment(commentId: number, _req: ReportRequest): Promise<{ reportId: number }> {
  return mockReport(`COMMENT:${commentId}`, mockComments.some((c) => c.commentId === commentId))
}
