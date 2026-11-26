// PUB-06-02 내 북마크 (/mypage/bookmarks, 와이어프레임 D-09). 로그인 회원 본인
// 담은 시각 최신순 20개 → 아래 끝에서 다음 20개. 카드 오른쪽 위 채워진 책갈피로 바로 해제(PUB-06-01).
// 정한 내용(11/26): 카드는 피그마 D-09대로 사진 없이 글자만, 한 줄에 2개.
// 볼 수 없게 된 게시물은 "볼 수 없는 게시물"만 보이고(제목·도시 숨김) 눌러도 상세로 가지 않는다. 해제는 된다.
// TODO(A에 요청): 비회원이 이 주소로 오면 로그인 화면으로 보내는 것은 A의 마이페이지 경로 보호(pjh/login)가 한다.
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../../../shared/components/Button'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Loading } from '../../../shared/components/Loading'
import { useToast } from '../../../shared/hooks/useToast'
import { formatRecordedAt } from '../../../shared/utils/date'
import { failMessage, getMyBookmarks, unbookmarkPost, type MyBookmarkItem } from '../api/communityApi.ts'
import BookmarkButton from '../components/BookmarkButton.tsx'
import './MyBookmarksPage.css'

type ListState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'done'; items: MyBookmarkItem[]; hasNext: boolean; page: number }

const LOAD_FAILED = '목록을 불러오지 못했어요. 다시 시도해 주세요.' // PUB-06-02 실패

// 카드 한 개: 제목 / "오사카 · 1일" / "민수 · 좋아요 30" (공개 목록 카드와 같은 글자, 사진 없음)
function BookmarkCard({ item, busy, onRemove }: { item: MyBookmarkItem; busy: boolean; onRemove: () => void }) {
  const icon = (
    <BookmarkButton bookmarked disabled={false} busy={busy} className="bookmark-card__icon" onClick={onRemove} />
  )
  if (!item.visible || !item.post) {
    return (
      <div className="bookmark-card bookmark-card--hidden">
        <p className="bookmark-card__title">볼 수 없는 게시물</p>
        {icon}
      </div>
    )
  }
  const post = item.post
  const meta = [post.authorNickname, `좋아요 ${post.likeCount}`, post.updatedAt ? `수정 ${formatRecordedAt(post.updatedAt)}` : null]
    .filter(Boolean)
    .join(' · ')
  return (
    <div className="bookmark-card">
      {/* 카드를 누르면 공개 상세(PUB-04-01) */}
      <Link to={`/community/${post.postId}`} className="bookmark-card__link">
        <p className="bookmark-card__title">{post.title}</p>
        <p className="bookmark-card__sub">
          {post.cityName} · {post.days}일
        </p>
        <p className="bookmark-card__meta">{meta}</p>
      </Link>
      {icon}
    </div>
  )
}

export default function MyBookmarksPage() {
  const toast = useToast()
  const [state, setState] = useState<ListState>({ status: 'loading' })
  const [reloadKey, setReloadKey] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreFailed, setMoreFailed] = useState(false)
  const [removing, setRemoving] = useState<number | null>(null) // 해제 중인 게시물 (처리 중 잠금)
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 첫 20개
  useEffect(() => {
    let ignore = false
    getMyBookmarks(undefined, 0)
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

  // 다음 20개: 지금 목록의 마지막 북마크 기준
  const loadMore = useCallback(() => {
    if (state.status !== 'done' || !state.hasNext || loadingMore || state.items.length === 0) return
    const last = state.items[state.items.length - 1]
    const nextPage = state.page + 1
    setLoadingMore(true)
    setMoreFailed(false)
    getMyBookmarks(last.bookmarkId, nextPage)
      .then((res) => {
        setState((prev) =>
          prev.status === 'done'
            ? { status: 'done', items: [...prev.items, ...res.content], hasNext: res.hasNext, page: nextPage }
            : prev,
        )
      })
      .catch(() => {
        setMoreFailed(true)
        toast.show({ message: LOAD_FAILED, type: 'error', action: { label: '다시 시도', onClick: () => setMoreFailed(false) } })
      })
      .finally(() => setLoadingMore(false))
  }, [state, loadingMore, toast])

  useEffect(() => {
    const target = sentinelRef.current
    if (!target || moreFailed) return
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) loadMore()
    })
    observer.observe(target)
    return () => observer.disconnect()
  }, [loadMore, moreFailed])

  // PUB-06-01 카드의 책갈피로 해제: 성공하면 카드를 빼고 "북마크에서 뺐어요"(3초)
  const remove = async (item: MyBookmarkItem) => {
    setRemoving(item.postId)
    try {
      await unbookmarkPost(item.postId)
      setState((prev) =>
        prev.status === 'done' ? { ...prev, items: prev.items.filter((i) => i.bookmarkId !== item.bookmarkId) } : prev,
      )
      toast.show({ message: '북마크에서 뺐어요' })
    } catch (e) {
      toast.show({ message: failMessage(e, '북마크에 담지 못했어요.'), type: 'error' })
    } finally {
      setRemoving(null)
    }
  }

  return (
    <main className="bookmarks-page">
      <h1 className="bookmarks-page__title">내 북마크</h1>

      {/* 불러오는 중: 빈 카드 3개(스켈레톤), 스피너 없음 */}
      {state.status === 'loading' && (
        <div className="bookmarks-skeleton">
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

      {state.status === 'done' && state.items.length === 0 && <EmptyState message="아직 북마크한 여행이 없어요." />}

      {state.status === 'done' && state.items.length > 0 && (
        <>
          <ul className="bookmarks-grid">
            {state.items.map((item) => (
              <li key={item.bookmarkId}>
                <BookmarkCard item={item} busy={removing === item.postId} onRemove={() => remove(item)} />
              </li>
            ))}
          </ul>
          {loadingMore && (
            <div className="bookmarks-skeleton bookmarks-skeleton--more">
              <Loading count={3} />
            </div>
          )}
          {state.hasNext && <div ref={sentinelRef} className="bookmarks-sentinel" aria-hidden="true" />}
        </>
      )}
    </main>
  )
}
