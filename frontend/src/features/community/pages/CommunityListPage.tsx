// PUB-03 공개 여행 목록·검색·정렬 (/community, 와이어프레임 D-01). 로그인 없이 볼 수 있음
// - PUB-03-01: 10개씩, 아래 끝에 닿으면 마지막 카드 기준으로 다음 10개(무한 스크롤)
// - PUB-03-02: 도시·일수·제목 검색, [초기화]
// - PUB-03-03: [최신순] [좋아요순], 바꾸면 지금 검색 조건 그대로 첫 10개부터
// 검색 조건과 정렬은 주소(?sort=LIKES&q=도쿄&cityId=1&days=3)에 남겨 뒤로가기·새로고침해도 유지한다.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Button } from '../../../shared/components/Button'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Loading } from '../../../shared/components/Loading'
import { useToast } from '../../../shared/hooks/useToast'
import { sliceChars } from '../../../shared/utils/validation'
import {
  getPublicPosts,
  SEARCH_DAYS_MAX,
  SEARCH_KEYWORD_MAX,
  type PostListItem,
  type PostListQuery,
  type PostSort,
} from '../api/communityApi.ts'
import PostCard from '../components/PostCard.tsx'
import PostSearchBar, { type SearchConditions } from '../components/PostSearchBar.tsx'
import './CommunityListPage.css'

type ListState =
  | { status: 'error'; key: string }
  | { status: 'done'; key: string; items: PostListItem[]; hasNext: boolean; page: number; total: number }

const LOAD_FAILED = '목록을 불러오지 못했어요. 다시 시도해 주세요.' // PUB-03-01 불러오기 실패

const SORTS: Array<{ value: PostSort; label: string }> = [
  { value: 'LATEST', label: '최신순' },
  { value: 'LIKES', label: '좋아요순' },
]

// 1 이상 max 이하 정수만 (주소를 손으로 고친 이상한 값은 조건 없음으로)
function readInt(value: string | null, max = Number.MAX_SAFE_INTEGER): number | null {
  if (value === null || !/^\d+$/.test(value)) return null
  const n = Number(value)
  return n >= 1 && n <= max ? n : null
}

// 주소 → 목록 조건
function readQuery(params: URLSearchParams): PostListQuery {
  const q = sliceChars((params.get('q') ?? '').trim(), SEARCH_KEYWORD_MAX)
  const cityId = readInt(params.get('cityId'))
  const days = readInt(params.get('days'), SEARCH_DAYS_MAX)
  return {
    sort: params.get('sort') === 'LIKES' ? 'LIKES' : 'LATEST', // 처음엔 최신순
    ...(q ? { q } : {}),
    ...(cityId !== null ? { cityId } : {}),
    ...(days !== null ? { days } : {}),
  }
}

// 목록 조건 → 주소 (기본값인 최신순·빈 조건은 남기지 않음)
function toParams(query: PostListQuery): Record<string, string> {
  const params: Record<string, string> = {}
  if (query.sort !== 'LATEST') params.sort = query.sort
  if (query.q) params.q = query.q
  if (query.cityId !== undefined) params.cityId = String(query.cityId)
  if (query.days !== undefined) params.days = String(query.days)
  return params
}

export default function CommunityListPage() {
  const toast = useToast()
  const [searchParams, setSearchParams] = useSearchParams()
  const queryKey = new URLSearchParams(toParams(readQuery(searchParams))).toString() // 조건이 같으면 같은 값
  const query = useMemo(() => readQuery(new URLSearchParams(queryKey)), [queryKey])
  const hasConditions = query.q !== undefined || query.cityId !== undefined || query.days !== undefined

  const [state, setState] = useState<ListState | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreFailed, setMoreFailed] = useState(false) // 다음 10개 실패: [다시 시도]를 누를 때까지 자동으로 안 부름
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 첫 10개 (처음·검색·정렬 바꿈·초기화·뒤로가기). 받은 조건(key)과 지금 조건이 다르면 불러오는 중
  const currentKey = `${queryKey}#${reloadKey}`
  const loading = state === null || state.key !== currentKey
  useEffect(() => {
    let ignore = false // 조건이 또 바뀌었거나 화면을 떠나면 이전 응답은 버림
    const key = `${queryKey}#${reloadKey}`
    getPublicPosts({ ...query, page: 0 })
      .then((res) => {
        if (!ignore) {
          setState({ status: 'done', key, items: res.content, hasNext: res.hasNext, page: 0, total: res.totalElements })
        }
      })
      .catch(() => {
        if (!ignore) setState({ status: 'error', key })
      })
    return () => {
      ignore = true
    }
  }, [query, queryKey, reloadKey])

  // 다음 10개: 지금까지 받은 마지막 카드의 postId 기준 (같은 카드가 두 번 나오지 않게)
  const loadMore = useCallback(() => {
    if (loading || state?.status !== 'done' || !state.hasNext || loadingMore) return
    const last = state.items[state.items.length - 1]
    const nextPage = state.page + 1
    const key = state.key
    setLoadingMore(true)
    setMoreFailed(false)
    getPublicPosts({ ...query, after: last.postId, page: nextPage })
      .then((res) => {
        toast.close()
        setState((prev) =>
          prev?.status === 'done' && prev.key === key // 그사이 조건이 바뀌었으면 버림
            ? { ...prev, items: [...prev.items, ...res.content], hasNext: res.hasNext, page: nextPage }
            : prev,
        )
      })
      .catch(() => {
        setMoreFailed(true)
        // 화면 아래 가운데 상태 창 + [다시 시도] (D-01 상태 창)
        toast.show({ message: LOAD_FAILED, type: 'error', action: { label: '다시 시도', onClick: () => setMoreFailed(false) } })
      })
      .finally(() => setLoadingMore(false))
  }, [loading, state, loadingMore, query, toast])

  // 목록 아래 끝(보이지 않는 줄)이 화면에 들어오면 다음 10개
  useEffect(() => {
    const target = sentinelRef.current
    if (!target || moreFailed) return
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) loadMore()
    })
    observer.observe(target)
    return () => observer.disconnect()
  }, [loadMore, moreFailed])

  // 조건을 바꾸는 동작: 이전 조건의 "다음 10개 실패" 안내는 닫고 바꿈
  const applyParams = (params: Record<string, string>) => {
    toast.close()
    setMoreFailed(false)
    setSearchParams(params)
  }

  // [다시 시도] (첫 10개 실패)
  const retry = () => setReloadKey((k) => k + 1)

  // PUB-03-02 [검색]·Enter: 지금 정렬은 그대로, 검색 조건만 바꿈
  const search = (c: SearchConditions) =>
    applyParams(
      toParams({
        sort: query.sort,
        ...(c.q ? { q: c.q } : {}),
        ...(c.cityId !== null ? { cityId: c.cityId } : {}),
        ...(c.days !== null ? { days: c.days } : {}),
      }),
    )

  // PUB-03-02 [초기화]·[조건 초기화]: 검색 조건을 모두 지우고 전체 목록 (정렬은 그대로)
  const reset = () => applyParams(toParams({ sort: query.sort }))

  // PUB-03-03 정렬 바꿈: 지금 검색 조건 그대로 첫 10개부터
  const changeSort = (sort: PostSort) => {
    if (sort !== query.sort) applyParams(toParams({ ...query, sort }))
  }

  const applied: SearchConditions = { q: query.q ?? '', cityId: query.cityId ?? null, days: query.days ?? null }
  const done = !loading && state?.status === 'done' ? state : null

  return (
    <main className="community-page">
      <h1 className="community-page__title">커뮤니티</h1>

      {/* 주소 조건이 바뀌면(뒤로가기 등) 입력칸도 그 조건으로 다시 채움 */}
      <PostSearchBar key={queryKey} applied={applied} onSearch={search} onReset={reset} />

      <div className="community-toolbar">
        {/* 결과 위 "검색 결과 n개" (정한 내용 11/26: 조건이 없어도 표시) */}
        <p className="community-toolbar__count">{done ? `검색 결과 ${done.total}개` : ''}</p>
        <div className="community-sort" role="group" aria-label="정렬">
          {SORTS.map((s) => (
            <Button
              key={s.value}
              variant={query.sort === s.value ? 'primary' : 'secondary'} // 선택된 버튼 강조
              aria-pressed={query.sort === s.value}
              onClick={() => changeSort(s.value)}
            >
              {s.label}
            </Button>
          ))}
        </div>
      </div>

      {/* 불러오는 중·검색 중·정렬 바꿈: 목록 자리에 빈 카드 3개(스켈레톤), 스피너 없음 */}
      {loading && (
        <div className="community-skeleton">
          <Loading count={3} />
        </div>
      )}

      {!loading && state?.status === 'error' && (
        <EmptyState
          message={LOAD_FAILED}
          action={
            <Button variant="secondary" onClick={retry}>
              다시 시도
            </Button>
          }
        />
      )}

      {done &&
        done.items.length === 0 &&
        (hasConditions ? (
          // PUB-03-02 결과 없음
          <EmptyState
            message="조건에 맞는 여행이 없어요."
            action={
              <Button variant="secondary" onClick={reset}>
                조건 초기화
              </Button>
            }
          />
        ) : (
          <EmptyState message="아직 공개된 여행이 없어요." />
        ))}

      {done && done.items.length > 0 && (
        <>
          <ul className="community-grid">
            {done.items.map((post) => (
              <li key={post.postId}>
                <PostCard post={post} />
              </li>
            ))}
          </ul>
          {/* 다음 10개 불러오는 중: 목록 아래에 빈 카드 3개 */}
          {loadingMore && (
            <div className="community-skeleton community-skeleton--more">
              <Loading count={3} />
            </div>
          )}
          {done.hasNext && <div ref={sentinelRef} className="community-sentinel" aria-hidden="true" />}
        </>
      )}
    </main>
  )
}
