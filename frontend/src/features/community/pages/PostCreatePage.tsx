// PUB-01-01·02·03 공유하기 → 공개 미리보기 → 게시 (/trips/:tripId/share, 와이어프레임 D-07·D-08). 여행 소유자만
// 1단계(D-07): "커뮤니티 게시글 작성" 창 — 공개용 제목·소개 → [다음]
// 2단계(D-08): 공개 미리보기 — "실제 날짜·경비·여행 참여자·메모는 공개되지 않아요." + 일정 → [게시]
// 정한 내용(11/27): D-07은 피그마에서 B의 여행 화면(B-03) 위에 뜨는 창이다. B의 [공유하기] 아이콘이 생기기 전까지는
//   이 주소에서 첫 단계로 창을 띄운다. 오른쪽 위 ×는 [취소]와 같은 동작. 미리보기 버튼은 기능정의서대로 [취소][이전][게시].
// TODO(B에 요청): 여행 화면 [공유하기] 아이콘(소유자에게만, 공개 중이면 비활성)에서 이 주소로 연결.
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Loading } from '../../../shared/components/Loading'
import { Modal } from '../../../shared/components/Modal'
import { useToast } from '../../../shared/hooks/useToast'
import {
  failMessage,
  getPostPreview,
  getShareStatus,
  publishPost,
  type PostPreview,
} from '../api/communityApi.ts'
import PostInfoForm from '../components/PostInfoForm.tsx'
import { hasPostTitle, isPostInfoChanged, toPostInfoRequest, type PostInfo } from '../utils/postInfo.ts'
import PublicSchedule from '../components/PublicSchedule.tsx'
import './PostCreatePage.css'

type Step =
  | { name: 'checking' } // 게시 상태 확인 중
  | { name: 'blocked'; message: string } // 공개 중·관리자 비공개·소유자 아님·이용 제한
  | { name: 'info' } // D-07 공개 정보 창
  | { name: 'preview' } // D-08 미리보기

type PreviewState = { status: 'loading' } | { status: 'error' } | { status: 'done'; preview: PostPreview }

const EMPTY_INFO: PostInfo = { title: '', intro: '' }

export default function PostCreatePage() {
  const { tripId: tripIdParam } = useParams()
  const tripId = Number(tripIdParam)
  const navigate = useNavigate()
  const toast = useToast()

  const [step, setStep] = useState<Step>({ name: 'checking' })
  const [initial, setInitial] = useState<PostInfo>(EMPTY_INFO) // 처음 값 (버릴지 확인용)
  const [info, setInfo] = useState<PostInfo>(EMPTY_INFO)
  const [titleError, setTitleError] = useState<string>()
  const [confirmDiscard, setConfirmDiscard] = useState(false)
  const [preview, setPreview] = useState<PreviewState>({ status: 'loading' })
  const [previewKey, setPreviewKey] = useState(0)
  const [confirmPublish, setConfirmPublish] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const requestKey = useRef(crypto.randomUUID()) // 게시 동작 1번에 1개, 다시 시도할 때는 같은 값

  // PUB-01-01 창을 열기 전 게시 상태 확인: 처음이면 빈칸, 비공개로 바꾼 게시물이면 예전 제목·소개
  useEffect(() => {
    let ignore = false
    getShareStatus(tripId)
      .then((s) => {
        if (ignore) return
        if (s.status === 'PUBLIC') {
          setStep({ name: 'blocked', message: '이미 공개한 여행이에요.' }) // 공개 중이면 공유하기 비활성
        } else if (s.status === 'ADMIN_HIDDEN') {
          setStep({ name: 'blocked', message: '관리자가 비공개로 바꾼 게시물이라 다시 공유할 수 없어요.' })
        } else {
          const start = { title: s.title ?? '', intro: s.intro ?? '' }
          setInitial(start)
          setInfo(start)
          setStep({ name: 'info' })
        }
      })
      .catch((e: unknown) => {
        if (!ignore) setStep({ name: 'blocked', message: failMessage(e, '미리보기를 만들지 못했어요. 다시 시도해 주세요.') })
      })
    return () => {
      ignore = true
    }
  }, [tripId])

  const retryPreview = useCallback(() => {
    toast.close()
    setPreview({ status: 'loading' })
    setPreviewKey((k) => k + 1)
  }, [toast])

  // PUB-01-02 미리보기 불러오기 (열기만 해서는 저장하지 않음)
  useEffect(() => {
    if (step.name !== 'preview') return
    let ignore = false
    getPostPreview(tripId)
      .then((p) => {
        if (!ignore) setPreview({ status: 'done', preview: p })
      })
      .catch((e: unknown) => {
        if (ignore) return
        setPreview({ status: 'error' })
        const ruleBroken = e instanceof ApiError && e.code !== null // 일정 없음·이용 제한 등은 다시 시도 없이 안내만
        toast.show({
          message: failMessage(e, '미리보기를 만들지 못했어요. 다시 시도해 주세요.'),
          type: 'error',
          action: ruleBroken ? undefined : { label: '다시 시도', onClick: retryPreview },
        })
      })
    return () => {
      ignore = true
    }
    // [다시 시도]는 previewKey를 바꿔 이 효과를 다시 부른다
  }, [step.name, tripId, previewKey, toast, retryPreview])

  const leave = () => navigate(`/trips/${tripId}`) // 여행 화면으로
  // [취소]·×: 입력한 글이 있으면 "작성 중인 내용을 버릴까요?"
  const cancel = () => (isPostInfoChanged(initial, info) ? setConfirmDiscard(true) : leave())

  // [다음]: 공개용 제목이 있을 때만
  const next = () => {
    if (!hasPostTitle(info)) {
      setTitleError('공개용 제목을 입력해 주세요.')
      return
    }
    setPreview({ status: 'loading' })
    setStep({ name: 'preview' })
  }

  // [이전]: 입력값 유지하고 공개 정보 창으로
  const back = () => {
    toast.close()
    setStep({ name: 'info' })
  }

  // PUB-01-03 게시: 확인 창 [게시] → "게시 중..." → "게시 완료" + 내 게시물 상세로
  const publish = async () => {
    if (publishing) return
    setPublishing(true)
    try {
      const { postId } = await publishPost(tripId, { requestKey: requestKey.current, ...toPostInfoRequest(info) })
      toast.show({ message: '게시 완료' })
      navigate(`/community/${postId}`)
    } catch (e) {
      setConfirmPublish(false)
      const ruleBroken = e instanceof ApiError && e.code !== null
      if (ruleBroken) requestKey.current = crypto.randomUUID() // 규칙 위반은 새 동작으로
      if (e instanceof ApiError && e.code === 'POST_TITLE_REQUIRED') {
        setTitleError(e.message)
        setStep({ name: 'info' })
        return
      }
      toast.show({
        message: failMessage(e, '게시하지 못했어요. 다시 시도해 주세요.'),
        type: 'error',
        action: ruleBroken ? undefined : { label: '다시 시도', onClick: publish }, // 같은 요청 식별값으로 다시
      })
    } finally {
      setPublishing(false)
    }
  }

  const discardDialog = (
    <ConfirmDialog
      open={confirmDiscard}
      message="작성 중인 내용을 버릴까요?"
      confirmLabel="버리기"
      cancelLabel="취소"
      onConfirm={leave}
      onCancel={() => setConfirmDiscard(false)}
    />
  )

  if (step.name === 'checking') {
    return (
      <main className="post-create">
        <Loading count={3} />
      </main>
    )
  }

  if (step.name === 'blocked') {
    return (
      <main className="post-create">
        <EmptyState message={step.message} />
      </main>
    )
  }

  if (step.name === 'info') {
    return (
      <main className="post-create post-create--dim">
        {/* D-07 커뮤니티 게시글 작성 창 */}
        <Modal
          open
          footer={
            <Button onClick={next} disabled={!hasPostTitle(info)}>
              다음
            </Button>
          }
        >
          <header className="post-create__modal-head">
            <h2 className="post-create__modal-title">커뮤니티 게시글 작성</h2>
            <button type="button" className="post-create__close" aria-label="닫기" onClick={cancel}>
              ×
            </button>
          </header>
          <PostInfoForm
            value={info}
            onChange={(v) => {
              setInfo(v)
              setTitleError(undefined)
            }}
            titleError={titleError}
          />
        </Modal>
        {discardDialog}
      </main>
    )
  }

  // D-08 공개 미리보기
  const intro = toPostInfoRequest(info).intro
  return (
    <main className="post-create">
      <p className="post-create__notice">실제 날짜·경비·여행 참여자·메모는 공개되지 않아요.</p>
      <header className="post-create__head">
        <div>
          <h1 className="post-create__title">{info.title.trim()}</h1>
          {intro && <p className="post-create__intro">{intro}</p>}
        </div>
        <div className="post-create__actions">
          <Button variant="secondary" onClick={cancel}>
            취소
          </Button>
          <Button variant="secondary" onClick={back}>
            이전
          </Button>
          <Button onClick={() => setConfirmPublish(true)} disabled={preview.status !== 'done'}>
            게시
          </Button>
        </div>
      </header>

      {/* 불러오는 중: 장소 목록 자리에 빈 카드 3개, 지도 자리는 빈 영역 */}
      {preview.status === 'loading' && (
        <div className="post-detail__body">
          <Loading count={3} />
          <div className="post-map" aria-label="지도" />
        </div>
      )}
      {preview.status === 'done' && <PublicSchedule schedule={preview.preview.schedule} />}

      <ConfirmDialog
        open={confirmPublish}
        message="다른 회원이 이 일정을 보고 복제할 수 있어요. 게시할까요?"
        confirmLabel="게시"
        cancelLabel="취소"
        confirmLoading={publishing}
        confirmLoadingText="게시 중..."
        onConfirm={publish}
        onCancel={() => setConfirmPublish(false)}
      />
      {discardDialog}
    </main>
  )
}
