// PUB-04-01·03 공개 여행 상세 (/community/:postId, 와이어프레임 D-02). 로그인 없이 볼 수 있음
// 위: 제목·작성자·도시·일수·좋아요·게시(수정) 시각·소개 + 오른쪽 버튼(PUB-04-03)
// 가운데: N일차 탭 → 왼쪽 장소 목록(구간 한 줄, 누르면 상세 펼침) / 오른쪽 지도(PUB-04-02)
// 내 게시물이면 [제목·소개 수정](PUB-02-05)·[비공개 전환](PUB-02-02)
// 아래: 댓글(PUB-08, CommentList)
// 일정 부분은 PublicSchedule (TODO(B 일정 화면 부품으로 교체)는 그 파일에)
import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Loading } from '../../../shared/components/Loading'
import { useToast } from '../../../shared/hooks/useToast'
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog'
import { Modal } from '../../../shared/components/Modal'
import { formatRecordedAt } from '../../../shared/utils/date'
import {
  bookmarkPost,
  failMessage,
  getPublicPostDetail,
  likePost,
  makePostPrivate,
  POST_NOT_VISIBLE,
  type PostDetail,
  unbookmarkPost,
  unlikePost,
  updatePostInfo,
} from '../api/communityApi.ts'
import BookmarkButton from '../components/BookmarkButton.tsx'
import CommentList from '../components/CommentList.tsx'
import LikeButton from '../components/LikeButton.tsx'
import PostInfoForm from '../components/PostInfoForm.tsx'
import { hasPostTitle, isPostInfoChanged, toPostInfoRequest, type PostInfo } from '../utils/postInfo.ts'
import PublicSchedule from '../components/PublicSchedule.tsx'
import './PostDetailPage.css'

type LoadState =
  | { status: 'loading' }
  | { status: 'hidden' } // 볼 수 없는 게시물
  | { status: 'error' }
  | { status: 'done'; post: PostDetail }

// PUB-02-05 제목·소개 수정 창 (화면 가운데, 배경은 내 게시물 상세를 어둡게)
// [저장]은 바뀐 내용이 있을 때만, 처리 중 "저장 중...", 성공하면 창 닫힘 + "수정 완료"
function PostEditModal({
  post,
  onSaved,
  onClose,
}: {
  post: PostDetail
  onSaved: (updated: PostDetail) => void
  onClose: () => void
}) {
  const toast = useToast()
  const initial: PostInfo = { title: post.title, intro: post.intro ?? '' }
  const [info, setInfo] = useState(initial)
  const [saving, setSaving] = useState(false)
  const [titleError, setTitleError] = useState<string>()
  const [confirmDiscard, setConfirmDiscard] = useState(false)
  const changed = isPostInfoChanged(initial, info)

  const save = async () => {
    if (saving || !changed || !hasPostTitle(info)) return
    setSaving(true)
    try {
      const updated = await updatePostInfo(post.postId, toPostInfoRequest(info))
      onSaved(updated)
      toast.show({ message: '수정 완료' })
    } catch (e) {
      const message = failMessage(e, '수정하지 못했어요. 다시 시도해 주세요.')
      if (e instanceof ApiError && e.code === 'POST_TITLE_REQUIRED') setTitleError(message)
      else toast.show({ message, type: 'error', action: e instanceof ApiError && e.code ? undefined : { label: '다시 시도', onClick: save } })
    } finally {
      setSaving(false)
    }
  }

  // [취소]: 고친 내용이 있으면 "작성 중인 내용을 버릴까요?"
  const cancel = () => (changed ? setConfirmDiscard(true) : onClose())

  return (
    <>
      <Modal
        open
        title="제목·소개 수정"
        footer={
          <>
            <Button variant="secondary" onClick={cancel} disabled={saving}>
              취소
            </Button>
            <Button onClick={save} disabled={!changed || !hasPostTitle(info)} loading={saving} loadingText="저장 중...">
              저장
            </Button>
          </>
        }
      >
        <PostInfoForm
          value={info}
          onChange={(next) => {
            setInfo(next)
            setTitleError(undefined)
          }}
          titleError={titleError}
        />
      </Modal>
      <ConfirmDialog
        open={confirmDiscard}
        message="작성 중인 내용을 버릴까요?"
        confirmLabel="버리기"
        cancelLabel="취소"
        onConfirm={onClose}
        onCancel={() => setConfirmDiscard(false)}
      />
    </>
  )
}

function PostBody({ post, onChanged }: { post: PostDetail; onChanged: (post: PostDetail) => void }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { viewer } = post

  // 비회원이 버튼을 누르면 로그인 화면으로. 로그인 후 이 게시물로 돌아오고, 누르려던 동작은 다시 눌러야 함
  // TODO(A에 요청): 로그인 후 돌아갈 주소를 넘기는 방법(A-01). 지금은 state.from으로 넘긴다.
  const requireLogin = (action: () => void) => () => {
    if (!viewer.loggedIn) {
      navigate('/login', { state: { from: `${location.pathname}${location.search}` } })
      return
    }
    action()
  }
  // TODO(D): 나머지 동작은 그 기능 단계에서 붙인다 (PUB-07 복제, PUB-09 신고)
  const notYet = () => {}
  const locked = viewer.loggedIn && !viewer.communityWritable // 이용 제한 회원: 버튼 모두 비활성
  const toast = useToast()

  // PUB-05 좋아요: 누르면 바로 채우고 ±1, 처리 중 잠금, 실패하면 되돌리고 안내
  const [like, setLike] = useState({ liked: viewer.liked, count: post.likeCount })
  const [likeBusy, setLikeBusy] = useState(false)
  const toggleLike = async () => {
    const before = like
    const adding = !before.liked
    setLike({ liked: adding, count: Math.max(0, before.count + (adding ? 1 : -1)) })
    setLikeBusy(true)
    try {
      const res = await (adding ? likePost(post.postId) : unlikePost(post.postId))
      setLike({ liked: res.liked, count: res.likeCount }) // 서버 값으로 맞춤
    } catch (e) {
      setLike(before) // 원래대로
      toast.show({
        message: failMessage(e, adding ? '좋아요를 누르지 못했어요.' : '좋아요를 취소하지 못했어요.'),
        type: 'error',
      })
    } finally {
      setLikeBusy(false)
    }
  }

  // PUB-06-01 북마크: 처리 중 잠금, 성공하면 채우고 "북마크 완료" / "북마크에서 뺐어요"(3초 뒤 자동으로 사라짐)
  const [bookmarked, setBookmarked] = useState(viewer.bookmarked)
  const [bookmarkBusy, setBookmarkBusy] = useState(false)
  const toggleBookmark = async () => {
    const adding = !bookmarked
    setBookmarkBusy(true)
    try {
      const res = await (adding ? bookmarkPost(post.postId) : unbookmarkPost(post.postId))
      setBookmarked(res.bookmarked)
      toast.show({ message: res.bookmarked ? '북마크 완료' : '북마크에서 뺐어요' })
    } catch (e) {
      toast.show({ message: failMessage(e, '북마크에 담지 못했어요.'), type: 'error' })
    } finally {
      setBookmarkBusy(false)
    }
  }

  // PUB-02-05 제목·소개 수정 창
  const [editing, setEditing] = useState(false)

  // PUB-02-02 비공개 전환: 확인 창 → "비공개 전환 중..." → "비공개 전환 완료" + 내 게시글로 이동
  const [confirmPrivate, setConfirmPrivate] = useState(false)
  const [privating, setPrivating] = useState(false)
  const goPrivate = async () => {
    if (privating) return
    setPrivating(true)
    try {
      await makePostPrivate(post.postId)
      toast.show({ message: '비공개 전환 완료' })
      navigate('/mypage/posts')
    } catch (e) {
      setConfirmPrivate(false)
      toast.show({
        message: failMessage(e, '비공개로 바꾸지 못했어요. 다시 시도해 주세요.'),
        type: 'error',
        action: e instanceof ApiError && e.code ? undefined : { label: '다시 시도', onClick: goPrivate },
      })
    } finally {
      setPrivating(false)
    }
  }

  // "지은 · 도쿄 · 4일 · 좋아요 12 · 게시 10.20 · 수정 10.28" (고친 적 없으면 게시 시각만)
  const meta = [
    post.authorNickname,
    post.cityName,
    `${post.days}일`,
    `좋아요 ${like.count}`,
    `게시 ${formatRecordedAt(post.publishedAt)}`,
    post.updatedAt ? `수정 ${formatRecordedAt(post.updatedAt)}` : null,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <>
      <header className="post-detail__head">
        <div className="post-detail__heading">
          <h1 className="post-detail__title">{post.title}</h1>
          <p className="post-detail__meta">{meta}</p>
        </div>
        {/* PUB-04-03 버튼: 내 게시물이면 [좋아요]·[제목·소개 수정]·[비공개 전환]만 */}
        <div className="post-detail__actions">
          <LikeButton
            count={like.count}
            liked={like.liked}
            disabled={locked}
            busy={likeBusy}
            onClick={requireLogin(toggleLike)}
          />
          {viewer.mine ? (
            <>
              <Button variant="secondary" disabled={locked} onClick={() => setEditing(true)}>
                제목·소개 수정
              </Button>
              <Button variant="secondary" disabled={locked} onClick={() => setConfirmPrivate(true)}>
                비공개 전환
              </Button>
            </>
          ) : (
            <>
              <BookmarkButton
                bookmarked={bookmarked}
                disabled={locked}
                busy={bookmarkBusy}
                onClick={requireLogin(toggleBookmark)}
              />
              <Button disabled={locked} onClick={requireLogin(notYet)}>
                내 여행으로 복제
              </Button>
              <Button variant="secondary" disabled={locked} onClick={requireLogin(notYet)}>
                신고
              </Button>
            </>
          )}
        </div>
      </header>
      {post.intro && <p className="post-detail__intro">{post.intro}</p>}

      <PublicSchedule schedule={post.schedule} />

      {/* PUB-08 댓글: 목록·입력칸·본인 댓글 수정·삭제 */}
      <CommentList postId={post.postId} loggedIn={viewer.loggedIn} />

      {editing && (
        <PostEditModal
          post={post}
          onSaved={(updated) => {
            setEditing(false)
            onChanged(updated) // 내 게시물 상세에 바로 반영
          }}
          onClose={() => setEditing(false)}
        />
      )}
      <ConfirmDialog
        open={confirmPrivate}
        message="북마크한 회원도 볼 수 없게 돼요. 이미 복제된 여행은 남아요."
        confirmLabel="비공개 전환"
        cancelLabel="취소"
        confirmLoading={privating}
        confirmLoadingText="비공개 전환 중..."
        onConfirm={goPrivate}
        onCancel={() => setConfirmPrivate(false)}
      />
    </>
  )
}

export default function PostDetailPage() {
  const { postId } = useParams()
  // 다른 게시물로 옮기면 처음부터 다시 불러옴(불러오는 중 → 1일차)
  return <PostDetailLoader key={postId} postIdParam={postId} />
}

function PostDetailLoader({ postIdParam }: { postIdParam: string | undefined }) {
  const navigate = useNavigate()
  const postId = Number(postIdParam)
  const validId = Number.isInteger(postId) && postId > 0
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let ignore = false // 화면을 떠났거나 다시 불러오면 이전 응답은 버림
    if (!validId) return
    getPublicPostDetail(postId)
      .then((post) => {
        if (!ignore) setState({ status: 'done', post })
      })
      .catch((e: unknown) => {
        if (ignore) return
        setState(e instanceof ApiError && e.code === POST_NOT_VISIBLE ? { status: 'hidden' } : { status: 'error' })
      })
    return () => {
      ignore = true
    }
  }, [postId, validId, reloadKey])

  const retry = () => {
    setState({ status: 'loading' })
    setReloadKey((k) => k + 1)
  }

  // 비공개·관리자 숨김·원래 여행 휴지통·없는 게시물(주소가 이상한 경우 포함): 내용 없이 안내 화면
  if (!validId || state.status === 'hidden') {
    return (
      <main className="post-detail">
        <EmptyState
          message="볼 수 없는 게시물이에요."
          action={
            <Button variant="secondary" onClick={() => navigate('/community')}>
              커뮤니티 목록으로
            </Button>
          }
        />
      </main>
    )
  }

  return (
    <main className="post-detail">
      {/* 불러오는 중: 장소 목록 자리에 빈 카드 3개(스켈레톤), 지도 자리는 빈 영역 */}
      {state.status === 'loading' && (
        <div className="post-detail__body">
          <Loading count={3} />
          <div className="post-map" aria-label="지도" />
        </div>
      )}
      {state.status === 'error' && (
        <EmptyState
          message="게시물을 불러오지 못했어요. 다시 시도해 주세요."
          action={
            <Button variant="secondary" onClick={retry}>
              다시 시도
            </Button>
          }
        />
      )}
      {state.status === 'done' && <PostBody post={state.post} onChanged={(post) => setState({ status: 'done', post })} />}
    </main>
  )
}
