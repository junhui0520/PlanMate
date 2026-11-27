// PUB-08 공개 댓글 영역 (와이어프레임 D-02 아래): 목록(PUB-08-02) + 입력칸(PUB-08-01) + 본인 댓글 수정·삭제(PUB-08-03·04)
// - 오래된 순 20개, 아래 끝까지 내리면 다음 20개
// - 한 줄: 닉네임 · 내용 · 작성 시각(오른쪽 끝), 고쳤으면 시각 아래 "수정됨"(시각과 같은 글자 모양)
// - 내 댓글은 오른쪽 [···] → [수정] [삭제], 남의 댓글은 [···] → [신고](로그인 회원, PUB-09-01)
// - 내용은 글자로만 표시한다(HTML·스크립트 실행 안 함, 링크 자동 실행 안 함)
// 정한 내용(11/27): 입력칸은 피그마 그림대로 목록 아래.
import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { Button } from '../../../shared/components/Button'
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog'
import { Loading } from '../../../shared/components/Loading'
import { useToast } from '../../../shared/hooks/useToast'
import { formatRecordedAt } from '../../../shared/utils/date'
import {
  deleteComment,
  editComment,
  failMessage,
  getComments,
  writeComment,
  type CommentItem,
} from '../api/communityApi.ts'
import CommentForm from './CommentForm.tsx'
import ReportModal from './ReportModal.tsx'
import './CommentList.css'

type ListState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'done'; items: CommentItem[]; hasNext: boolean; page: number }

const LOAD_FAILED = '댓글을 불러오지 못했어요.' // PUB-08-02 실패
const COMMON_FAILED = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.' // 수정 실패 문구는 기능정의서에 없어 공통 문구

// 댓글 [···] 메뉴: 내 댓글은 [수정] [삭제], 남의 댓글은 [신고]
function CommentMenu({ items }: { items: Array<{ label: string; onClick: () => void }> }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="comment-menu">
      <button
        type="button"
        className="comment-menu__toggle"
        aria-label="댓글 메뉴"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        ···
      </button>
      {open && (
        <div className="comment-menu__list" role="menu">
          {items.map((item) => (
            <button
              key={item.label}
              type="button"
              role="menuitem"
              onClick={() => {
                setOpen(false)
                item.onClick()
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export default function CommentList({ postId, loggedIn }: { postId: number; loggedIn: boolean }) {
  const toast = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  const [state, setState] = useState<ListState>({ status: 'loading' })
  const [reloadKey, setReloadKey] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreFailed, setMoreFailed] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<CommentItem | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [reportId, setReportId] = useState<number | null>(null) // PUB-09-01 신고할 댓글
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 처음 20개
  useEffect(() => {
    let ignore = false
    getComments(postId, undefined, 0)
      .then((res) => {
        if (!ignore) setState({ status: 'done', items: res.content, hasNext: res.hasNext, page: 0 })
      })
      .catch(() => {
        if (!ignore) setState({ status: 'error' })
      })
    return () => {
      ignore = true
    }
  }, [postId, reloadKey])

  const retry = () => {
    setState({ status: 'loading' })
    setReloadKey((k) => k + 1)
  }

  // 다음 20개: 마지막 댓글 기준
  const loadMore = useCallback(() => {
    if (state.status !== 'done' || !state.hasNext || loadingMore || state.items.length === 0) return
    const last = state.items[state.items.length - 1]
    const nextPage = state.page + 1
    setLoadingMore(true)
    setMoreFailed(false)
    getComments(postId, last.commentId, nextPage)
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
  }, [state, loadingMore, postId, toast])

  useEffect(() => {
    const target = sentinelRef.current
    if (!target || moreFailed) return
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) loadMore()
    })
    observer.observe(target)
    return () => observer.disconnect()
  }, [loadMore, moreFailed])

  // PUB-08-01 [등록]: 아직 못 불러온 댓글이 있으면 맨 아래까지 불러온 뒤 내 댓글을 맨 아래에 붙임 (별도 안내 없음)
  const write = async (content: string): Promise<boolean> => {
    try {
      const created = await writeComment(postId, content)
      let items = state.status === 'done' ? state.items : []
      let hasNext = state.status === 'done' && state.hasNext
      let page = state.status === 'done' ? state.page : 0
      while (hasNext) {
        const res = await getComments(postId, items[items.length - 1]?.commentId, page + 1)
        items = [...items, ...res.content]
        hasNext = res.hasNext
        page += 1
      }
      if (!items.some((c) => c.commentId === created.commentId)) items = [...items, created]
      setState({ status: 'done', items, hasNext: false, page })
      return true
    } catch (e) {
      // 너무 빨리 등록·이용 제한·볼 수 없는 게시물은 서버 문구, 그 밖은 등록 실패 문구
      toast.show({ message: failMessage(e, '댓글을 등록하지 못했어요. 다시 시도해 주세요.'), type: 'error' })
      return false
    }
  }

  // PUB-08-03 [저장]: 내용 바뀌고 "수정됨"
  const save = async (commentId: number, content: string): Promise<boolean> => {
    try {
      const updated = await editComment(commentId, content)
      setState((prev) =>
        prev.status === 'done'
          ? { ...prev, items: prev.items.map((c) => (c.commentId === commentId ? updated : c)) }
          : prev,
      )
      setEditingId(null)
      return true
    } catch (e) {
      toast.show({ message: failMessage(e, COMMON_FAILED), type: 'error' })
      return false
    }
  }

  // PUB-08-04 삭제 확인 창 [삭제] → "삭제 중..." → 목록에서 바로 제거
  const confirmDelete = async () => {
    const target = deleteTarget
    if (!target || deleting) return
    setDeleting(true)
    try {
      await deleteComment(target.commentId)
      setState((prev) =>
        prev.status === 'done' ? { ...prev, items: prev.items.filter((c) => c.commentId !== target.commentId) } : prev,
      )
    } catch (e) {
      toast.show({ message: failMessage(e, '댓글을 지우지 못했어요.'), type: 'error' })
    } finally {
      setDeleting(false)
      setDeleteTarget(null)
    }
  }

  return (
    <section className="post-comments" aria-label="댓글">
      <h2 className="post-comments__title">댓글</h2>

      {/* 불러오는 중: 댓글 자리에 빈 줄 3개(스켈레톤) */}
      {state.status === 'loading' && <Loading count={3} variant="line" />}

      {state.status === 'error' && (
        <div className="post-comments__error">
          <p>{LOAD_FAILED}</p>
          <Button variant="text" onClick={retry}>
            다시 시도
          </Button>
        </div>
      )}

      {state.status === 'done' && state.items.length === 0 && <p className="post-comments__empty">첫 댓글을 남겨 보세요.</p>}

      {state.status === 'done' && state.items.length > 0 && (
        <ul className="comment-list">
          {state.items.map((c) => (
            <li key={c.commentId} className="comment">
              <span className="comment__author">
                {c.authorNickname ?? ''}
                {c.mine ? ' (나)' : ''}
              </span>
              {editingId === c.commentId ? (
                <div className="comment__body">
                  <CommentForm
                    initial={c.content}
                    submitLabel="저장"
                    loadingLabel="저장 중..."
                    onSubmit={(content) => save(c.commentId, content)}
                    onCancel={() => setEditingId(null)}
                  />
                </div>
              ) : (
                <>
                  {/* 글자로만 표시: React가 그대로 그리므로 HTML·스크립트가 실행되지 않음 */}
                  <p className="comment__body comment__content">{c.content}</p>
                  <span className="comment__time">
                    {formatRecordedAt(c.createdAt)}
                    {c.edited && <span className="comment__edited">수정됨</span>}
                  </span>
                  <span className="comment__menu-slot">
                    {c.mine ? (
                      <CommentMenu
                        items={[
                          { label: '수정', onClick: () => setEditingId(c.commentId) },
                          { label: '삭제', onClick: () => setDeleteTarget(c) },
                        ]}
                      />
                    ) : (
                      loggedIn && <CommentMenu items={[{ label: '신고', onClick: () => setReportId(c.commentId) }]} />
                    )}
                  </span>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
      {loadingMore && <Loading count={3} variant="line" />}
      {state.status === 'done' && state.hasNext && (
        <div ref={sentinelRef} className="post-comments__sentinel" aria-hidden="true" />
      )}

      {/* PUB-08-01 입력칸. 비회원: "로그인하고 댓글을 남겨 보세요" + [로그인] (로그인 후 이 게시물로 돌아옴) */}
      {loggedIn ? (
        <CommentForm submitLabel="등록" loadingLabel="등록 중..." onSubmit={write} />
      ) : (
        <div className="post-comments__login">
          <p>로그인하고 댓글을 남겨 보세요</p>
          <Button
            variant="secondary"
            onClick={() => navigate('/login', { state: { from: `${location.pathname}${location.search}` } })}
          >
            로그인
          </Button>
        </div>
      )}

      {reportId !== null && <ReportModal target={{ type: 'COMMENT', id: reportId }} onClose={() => setReportId(null)} />}

      <ConfirmDialog
        open={deleteTarget !== null}
        message="댓글을 삭제할까요?"
        confirmLabel="삭제"
        cancelLabel="취소"
        confirmLoading={deleting}
        confirmLoadingText="삭제 중..."
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </section>
  )
}
