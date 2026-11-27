// PUB-07-01·02 내 여행으로 복제 창 (와이어프레임 D-04). 공개 상세의 [내 여행으로 복제]에서 연다.
// - 위쪽 "내 여행 n/3" (B의 TRP-01-01과 같음), 이미 3개면 창 대신 B 문구 안내
// - 시작일: 오늘 ~ 1년 후만 고를 수 있음 / 종료일: 읽기 전용, 시작일 + 공개본 일수 - 1 ("2026.11.22(일)")
// - 여행 인원: [-] 1 [+] (1~20, 기본 1, 1에서 [-]·20에서 [+] 비활성)
// - [복제]는 시작일을 골랐을 때만, 처리 중 "복제 중..." → "복제 완료" → 새 여행 일정 화면
// 정한 내용(11/27): 날짜 칸은 브라우저 기본 날짜 칸, 범위는 한국 기준 오늘~1년 후(정확한 "여행지 오늘" 검사는 B 서버).
//   이미 3개일 때는 피그마의 안내 창을 따르되 [구독 알아보기(준비 중)]은 B 기능이라 [확인]만 둔다.
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog'
import { Modal } from '../../../shared/components/Modal'
import { useToast } from '../../../shared/hooks/useToast'
import { formatDate, toIsoDate } from '../../../shared/utils/date'
import {
  checkClonable,
  CLONE_PEOPLE_MAX,
  CLONE_PEOPLE_MIN,
  cloneToMyTrip,
  failMessage,
  type CloneCheck,
} from '../api/communityApi.ts'
import './CloneModal.css'

type CheckState = { status: 'checking' } | { status: 'ok'; check: CloneCheck } | { status: 'limit'; message: string }

// "2026-12-01" → Date (로컬, 시간대 변환 없이)
function parseDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function addDays(value: string, days: number): string {
  const date = parseDate(value)
  date.setDate(date.getDate() + days)
  return toIsoDate(date)
}

export default function CloneModal({
  postId,
  days,
  onClose,
}: {
  postId: number
  days: number // 공개본 일수 (종료일 자동 계산)
  onClose: () => void
}) {
  const navigate = useNavigate()
  const toast = useToast()
  const [check, setCheck] = useState<CheckState>({ status: 'checking' })
  const [startDate, setStartDate] = useState('')
  const [people, setPeople] = useState(1)
  const [peopleText, setPeopleText] = useState('1')
  const [cloning, setCloning] = useState(false)
  const [errors, setErrors] = useState<{ startDate?: string; people?: string }>({})
  const requestKey = useRef(crypto.randomUUID()) // 복제 동작 1번에 1개, 다시 시도할 때는 같은 값

  const today = toIsoDate(new Date())
  const oneYearLater = (() => {
    const d = new Date()
    d.setFullYear(d.getFullYear() + 1)
    return toIsoDate(d)
  })()
  const endDate = startDate ? addDays(startDate, days - 1) : ''

  // 창을 열 때: 게시물이 지금 보이는지 + "내 여행 n/3"
  useEffect(() => {
    let ignore = false
    checkClonable(postId)
      .then((c) => {
        if (!ignore) setCheck({ status: 'ok', check: c })
      })
      .catch((e: unknown) => {
        if (ignore) return
        if (e instanceof ApiError && e.code === 'TRIP_LIMIT_EXCEEDED') {
          setCheck({ status: 'limit', message: e.message })
          return
        }
        // 이용 제한·볼 수 없는 게시물 등: 창을 닫고 상태 창으로 안내
        toast.show({ message: failMessage(e, '여행을 복제하지 못했어요. 다시 시도해 주세요.'), type: 'error' })
        onClose()
      })
    return () => {
      ignore = true
    }
  }, [postId, toast, onClose])

  // 인원 칸: 숫자만, 1~20 (범위 밖은 칸 아래 안내)
  const changePeople = (text: string) => {
    const digits = text.replace(/[^0-9]/g, '')
    setPeopleText(digits)
    const n = Number(digits)
    if (digits !== '' && n >= CLONE_PEOPLE_MIN && n <= CLONE_PEOPLE_MAX) {
      setPeople(n)
      setErrors((prev) => ({ ...prev, people: undefined }))
    } else {
      setErrors((prev) => ({ ...prev, people: '여행 인원은 1~20 사이의 정수로 입력해 주세요.' }))
    }
  }
  const step = (delta: number) => {
    const n = Math.min(CLONE_PEOPLE_MAX, Math.max(CLONE_PEOPLE_MIN, people + delta))
    setPeople(n)
    setPeopleText(String(n))
    setErrors((prev) => ({ ...prev, people: undefined }))
  }

  const canClone = check.status === 'ok' && startDate !== '' && !errors.people && !errors.startDate

  // PUB-07-02 [복제]
  const submit = async () => {
    if (!canClone || cloning) return
    setCloning(true)
    try {
      const { tripId } = await cloneToMyTrip(postId, { requestKey: requestKey.current, startDate, peopleCount: people })
      toast.show({ message: '복제 완료' })
      // PUB-07-03 재확인 안내는 새 여행 일정 화면(B)이 이 표시를 보고 한 번만 띄운다. TODO(B에 요청)
      navigate(`/trips/${tripId}`, { state: { cloned: true } })
    } catch (e) {
      const code = e instanceof ApiError ? e.code : null
      if (code) requestKey.current = crypto.randomUUID() // 규칙 위반은 고친 뒤 새 동작으로
      if (code === 'TRIP_START_DATE_OUT_OF_RANGE' || code === 'TRIP_END_DATE_BEFORE_START' || code === 'TRIP_PERIOD_TOO_LONG') {
        setErrors((prev) => ({ ...prev, startDate: (e as ApiError).message })) // 시작일 칸 아래
      } else if (code === 'TRIP_LIMIT_EXCEEDED') {
        setCheck({ status: 'limit', message: (e as ApiError).message })
      } else {
        toast.show({
          message: failMessage(e, '여행을 복제하지 못했어요. 다시 시도해 주세요.'),
          type: 'error',
          action: code ? undefined : { label: '다시 시도', onClick: submit }, // 같은 요청 식별값으로 다시
        })
      }
    } finally {
      setCloning(false)
    }
  }

  // 이미 3개: B의 TRP-01-01과 같은 안내 창
  if (check.status === 'limit') {
    return <ConfirmDialog open message={check.message} confirmLabel="확인" hideCancel onConfirm={onClose} />
  }

  return (
    <Modal
      open
      footer={
        <Button onClick={submit} disabled={!canClone} loading={cloning} loadingText="복제 중...">
          복제
        </Button>
      }
    >
      <header className="clone-modal__head">
        <h2 className="clone-modal__title">내 여행으로 복제</h2>
        <button type="button" className="clone-modal__close" aria-label="닫기" onClick={onClose} disabled={cloning}>
          ×
        </button>
      </header>
      <p className="clone-modal__count">
        {check.status === 'ok' ? `내 여행 ${check.check.activeTripCount}/${check.check.maxTripCount}` : ' '}
      </p>

      <div className="clone-modal__fields">
        <label className="pm-field">
          <span className="pm-field__label">시작일</span>
          <input
            type="date"
            className="pm-field__input"
            min={today}
            max={oneYearLater}
            value={startDate}
            disabled={cloning}
            aria-invalid={errors.startDate ? true : undefined}
            onChange={(e) => {
              setStartDate(e.target.value)
              setErrors((prev) => ({ ...prev, startDate: undefined }))
            }}
          />
          {errors.startDate && <span className="pm-field__error">{errors.startDate}</span>}
        </label>

        <label className="pm-field">
          <span className="pm-field__label">종료일</span>
          <input className="pm-field__input" value={endDate ? formatDate(endDate) : ''} readOnly disabled />
          <span className="pm-field__hint">시작일을 고르면 자동으로 채워져요</span>
        </label>

        <div className="pm-field">
          <span className="pm-field__label">여행 인원</span>
          <div className="clone-modal__stepper">
            <Button variant="secondary" aria-label="인원 줄이기" disabled={cloning || people <= CLONE_PEOPLE_MIN} onClick={() => step(-1)}>
              −
            </Button>
            <input
              className="pm-field__input clone-modal__people"
              inputMode="numeric"
              aria-label="여행 인원"
              value={peopleText}
              disabled={cloning}
              onChange={(e) => changePeople(e.target.value)}
            />
            <Button variant="secondary" aria-label="인원 늘리기" disabled={cloning || people >= CLONE_PEOPLE_MAX} onClick={() => step(1)}>
              +
            </Button>
          </div>
          {errors.people && <span className="pm-field__error">{errors.people}</span>}
        </div>
      </div>
    </Modal>
  )
}
