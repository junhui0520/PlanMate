// PUB-02-04 내 게시글 (/mypage/posts, 와이어프레임 D-10). 로그인 회원 본인
// 게시·수정 시각 최신순 10개 → 아래 끝에서 다음 10개.
// 카드: 제목 + "공개" / "비공개" / "관리자 비공개", "게시 10.20 · 수정 10.28 · 좋아요 12 · 댓글 3"
// 버튼: 공개 → [비공개 전환](PUB-02-02), 비공개 → [공개 전환](공유하기 흐름 PUB-01-01), 관리자 비공개 → 버튼 없음
// 공개 중인 카드를 누르면 내 게시물 상세(PUB-04-01·03). 이용 제한 회원은 목록만 보고 버튼은 비활성.
// TODO(A에 요청): 비회원이 이 주소로 오면 로그인 화면으로 보내는 것은 A의 마이페이지 경로 보호가 한다.
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Loading } from '../../../shared/components/Loading'
import { useToast } from '../../../shared/hooks/useToast'
import { formatRecordedAt } from '../../../shared/utils/date'
import { failMessage, getMyPosts, makePostPrivate, type MyPostItem } from '../api/communityApi.ts'
import './MyPostsPage.css'

type ListState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'done'; items: MyPostItem[]; hasNext: boolean; page: number }

const LOAD_FAILED = '내 게시글을 불러오지 못했어요. 다시 시도해 주세요.' // PUB-02-04 조회 실패

// 상태 글자 (색 구분 없이 글자로만, PUB-02-03)
const STATUS_LABEL: Record<MyPostItem['status'], string> = {
  PUBLIC: '공개',
  PRIVATE: '비공개',
  ADMIN_HIDDEN: '관리자 비공개',
}

function MyPostCard({
  item,
  onPrivate,
  onPublic,
}: {
  item: MyPostItem
  onPrivate: () => void
  onPublic: () => void
}) {
  const meta = [
    `게시 ${formatRecordedAt(item.publishedAt)}`,
    item.updatedAt ? `수정 ${formatRecordedAt(item.updatedAt)}` : null,
    `좋아요 ${item.likeCount}`,
    `댓글 ${item.commentCount}`,
  ]
    .filter(Boolean)
    .join(' · ')
  const body = (
    <>
      <p className="my-post__title">
        {item.title}
        <span className="my-post__status">{STATUS_LABEL[item.status]}</span>
      </p>
      <p className="my-post__meta">{meta}</p>
    </>
  )
  return (
    <div className="my-post">
      {/* 공개 중인 카드만 내 게시물 상세로 */}
      {item.status === 'PUBLIC' ? (
        <Link to={`/community/${item.postId}`} className="my-post__body">
          {body}
        </Link>
      ) : (
        <div className="my-post__body">{body}</div>
      )}
      {item.status === 'PUBLIC' && (
        <Button variant="secondary" disabled={!item.communityWritable} onClick={onPrivate}>
          비공개 전환
        </Button>
      )}
      {item.status === 'PRIVATE' && (
        <Button variant="secondary" disabled={!item.communityWritable} onClick={onPublic}>
          공개 전환
        </Button>
      )}
    </div>
  )
}

export default function MyPostsPage() {
  const toast = useToast()
  const navigate = useNavigate()
  const [state, setState] = useState<ListState>({ status: 'loading' })
  const [reloadKey, setReloadKey] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreFailed, setMoreFailed] = useState(false)
  const [privateTarget, setPrivateTarget] = useState<MyPostItem | null>(null) // PUB-02-02 확인 창
  const [privating, setPrivating] = useState(false)
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 첫 10개
  useEffect(() => {
    let ignore = false
    getMyPosts(undefined, 0)
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

  // 다음 10개: 마지막 카드 기준
  const loadMore = useCallback(() => {
    if (state.status !== 'done' || !state.hasNext || loadingMore || state.items.length === 0) return
    const last = state.items[state.items.length - 1]
    const nextPage = state.page + 1
    setLoadingMore(true)
    setMoreFailed(false)
    getMyPosts(last.postId, nextPage)
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

  // PUB-02-02 비공개 전환: 확인 창 → "비공개 전환 중..." → 카드 상태 '비공개' + "비공개 전환 완료"
  const goPrivate = async () => {
    const target = privateTarget
    if (!target || privating) return
    setPrivating(true)
    try {
      await makePostPrivate(target.postId)
      setState((prev) =>
        prev.status === 'done'
          ? {
              ...prev,
              items: prev.items.map((i) => (i.postId === target.postId ? { ...i, status: 'PRIVATE' as const } : i)),
            }
          : prev,
      )
      setPrivateTarget(null)
      toast.show({ message: '비공개 전환 완료' })
    } catch (e) {
      setPrivateTarget(null)
      toast.show({
        message: failMessage(e, '비공개로 바꾸지 못했어요. 다시 시도해 주세요.'),
        type: 'error',
        action: e instanceof ApiError && e.code ? undefined : { label: '다시 시도', onClick: () => setPrivateTarget(target) },
      })
    } finally {
      setPrivating(false)
    }
  }

  return (
    <main className="my-posts-page">
      <h1 className="my-posts-page__title">내 게시글</h1>

      {/* 불러오는 중: 빈 카드 3개(스켈레톤), 스피너 없음 */}
      {state.status === 'loading' && (
        <div className="my-posts-skeleton">
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

      {state.status === 'done' && state.items.length === 0 && <EmptyState message="아직 공유한 게시물이 없어요." />}

      {state.status === 'done' && state.items.length > 0 && (
        <>
          <ul className="my-posts-list">
            {state.items.map((item) => (
              <li key={item.postId}>
                <MyPostCard
                  item={item}
                  onPrivate={() => setPrivateTarget(item)}
                  onPublic={() => navigate(`/trips/${item.tripId}/share`)} // 공유하기 흐름 (예전 제목·소개가 들어 있음)
                />
              </li>
            ))}
          </ul>
          {loadingMore && (
            <div className="my-posts-skeleton my-posts-skeleton--more">
              <Loading count={3} />
            </div>
          )}
          {state.hasNext && <div ref={sentinelRef} className="my-posts-sentinel" aria-hidden="true" />}
        </>
      )}

      <ConfirmDialog
        open={privateTarget !== null}
        message="북마크한 회원도 볼 수 없게 돼요. 이미 복제된 여행은 남아요."
        confirmLabel="비공개 전환"
        cancelLabel="취소"
        confirmLoading={privating}
        confirmLoadingText="비공개 전환 중..."
        onConfirm={goPrivate}
        onCancel={() => setPrivateTarget(null)}
      />
    </main>
  )
}
