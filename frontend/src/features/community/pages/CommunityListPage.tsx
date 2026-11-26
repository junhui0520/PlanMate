// PUB-03-01 공개 여행 목록 (/community, 와이어프레임 D-01). 로그인 없이 볼 수 있음
// 최신순 10개 → 아래 끝에 닿으면 마지막 카드 기준으로 다음 10개(무한 스크롤)
// TODO(D): 3단계에서 검색(PUB-03-02)·정렬(PUB-03-03)과 주소에 조건 남기기를 붙인다.
import { useCallback, useEffect, useRef, useState } from 'react'
import { Button } from '../../../shared/components/Button'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Loading } from '../../../shared/components/Loading'
import { useToast } from '../../../shared/hooks/useToast'
import { getPublicPosts, type PostListItem, type PostListQuery } from '../api/communityApi.ts'
import PostCard from '../components/PostCard.tsx'
import './CommunityListPage.css'

type ListState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'done'; items: PostListItem[]; hasNext: boolean; page: number }

const query: PostListQuery = { sort: 'LATEST' } // 3단계에서 주소 조건(검색·정렬)으로 바뀜
const LOAD_FAILED = '목록을 불러오지 못했어요. 다시 시도해 주세요.' // PUB-03-01 불러오기 실패

export default function CommunityListPage() {
  const toast = useToast()
  const [state, setState] = useState<ListState>({ status: 'loading' })
  const [reloadKey, setReloadKey] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreFailed, setMoreFailed] = useState(false) // 다음 10개 실패: [다시 시도]를 누를 때까지 자동으로 안 부름
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 첫 10개
  useEffect(() => {
    let ignore = false // 화면을 떠났거나 다시 불러오면 이전 응답은 버림
    getPublicPosts({ ...query, page: 0 })
      .then((res) => {
        if (!ignore) setState({ status: 'done', items: res.content, hasNext: res.hasNext, page: 0 })
      })
      .catch(() => {
        if (!ignore) setState({ status: 'error' })
      })
    return () => {
      ignore = true
    }
  }, [reloadKey])

  const retry = () => {
    setState({ status: 'loading' })
    setReloadKey((k) => k + 1)
  }

  // 다음 10개: 지금까지 받은 마지막 카드의 postId 기준 (같은 카드가 두 번 나오지 않게)
  const loadMore = useCallback(() => {
    if (state.status !== 'done' || !state.hasNext || loadingMore) return
    const last = state.items[state.items.length - 1]
    const nextPage = state.page + 1
    setLoadingMore(true)
    setMoreFailed(false)
    getPublicPosts({ ...query, after: last.postId, page: nextPage })
      .then((res) => {
        toast.close()
        setState((prev) =>
          prev.status === 'done'
            ? { status: 'done', items: [...prev.items, ...res.content], hasNext: res.hasNext, page: nextPage }
            : prev,
        )
      })
      .catch(() => {
        setMoreFailed(true)
        // 화면 아래 가운데 상태 창 + [다시 시도] (D-01 상태 창)
        toast.show({ message: LOAD_FAILED, type: 'error', action: { label: '다시 시도', onClick: () => setMoreFailed(false) } })
      })
      .finally(() => setLoadingMore(false))
  }, [state, loadingMore, toast])

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

  return (
    <main className="community-page">
      <h1 className="community-page__title">커뮤니티</h1>

      {/* 불러오는 중: 목록 자리에 빈 카드 3개(스켈레톤), 스피너 없음 */}
      {state.status === 'loading' && (
        <div className="community-skeleton">
          <Loading count={3} />
        </div>
      )}

      {state.status === 'error' && (
        <EmptyState
          message={LOAD_FAILED}
          action={
            <Button variant="secondary" onClick={retry}>
              다시 시도
            </Button>
          }
        />
      )}

      {state.status === 'done' && state.items.length === 0 && <EmptyState message="아직 공개된 여행이 없어요." />}

      {state.status === 'done' && state.items.length > 0 && (
        <>
          <ul className="community-grid">
            {state.items.map((post) => (
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
          {state.hasNext && <div ref={sentinelRef} className="community-sentinel" aria-hidden="true" />}
        </>
      )}
    </main>
  )
}
