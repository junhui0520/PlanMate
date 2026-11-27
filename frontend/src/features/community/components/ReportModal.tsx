// PUB-09-01 신고 창 (와이어프레임 D-06). 게시물 [신고] 또는 남의 댓글 [···] > [신고]에서 연다(로그인 회원).
// - 사유 5개 중 하나: 스팸·광고 / 욕설·괴롭힘 / 개인정보 노출 / 부적절한 닉네임 / 기타(직접 입력)
// - '기타'를 고르면 상세 내용 칸(여러 줄, 0/300, 300자에서 더 입력되지 않음, 줄바꿈 말고 다른 제어문자는 공백 1칸)
// - [신고]는 사유를 골랐을 때(기타면 내용까지)만, 처리 중 "신고 중..." → "신고 접수 완료"(3초)
// 정한 내용(11/27): 버튼 이름은 기능정의서대로 [신고] (피그마 그림은 [확인]).
import { useId, useState, type ChangeEvent } from 'react'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { Modal } from '../../../shared/components/Modal'
import { useToast } from '../../../shared/hooks/useToast'
import { countChars, isBlank, replaceControlChars, sliceChars } from '../../../shared/utils/validation'
import {
  failMessage,
  REPORT_DETAIL_MAX,
  reportComment,
  reportPost,
  type ReportReason,
} from '../api/communityApi.ts'
import './ReportModal.css'

const REASONS: Array<{ value: ReportReason; label: string }> = [
  { value: 'SPAM', label: '스팸·광고' },
  { value: 'ABUSE', label: '욕설·괴롭힘' },
  { value: 'PRIVACY', label: '개인정보 노출' },
  { value: 'NICKNAME', label: '부적절한 닉네임' },
  { value: 'ETC', label: '기타(직접 입력)' },
]

export type ReportTarget = { type: 'POST'; id: number } | { type: 'COMMENT'; id: number }

export default function ReportModal({ target, onClose }: { target: ReportTarget; onClose: () => void }) {
  const toast = useToast()
  const detailId = useId()
  const [reason, setReason] = useState<ReportReason | null>(null)
  const [detail, setDetail] = useState('')
  const [busy, setBusy] = useState(false)
  const canSubmit = reason !== null && (reason !== 'ETC' || !isBlank(detail))

  const changeDetail = (e: ChangeEvent<HTMLTextAreaElement>) => {
    const lines = e.target.value.replace(/\r\n?/g, '\n').split('\n').map(replaceControlChars)
    setDetail(sliceChars(lines.join('\n'), REPORT_DETAIL_MAX))
  }

  const submit = async () => {
    if (!canSubmit || busy || reason === null) return
    setBusy(true)
    const req = { reason, detail: reason === 'ETC' ? detail.trim() : null }
    try {
      await (target.type === 'POST' ? reportPost(target.id, req) : reportComment(target.id, req))
      toast.show({ message: '신고 접수 완료' })
      onClose()
    } catch (e) {
      // 이미 접수·대상 없음·이용 제한은 서버 문구로 안내하고 창을 닫는다. 그 밖의 실패는 창을 남겨 다시 누를 수 있게.
      const ruleBroken = e instanceof ApiError && e.code !== null
      toast.show({ message: failMessage(e, '신고하지 못했어요. 다시 시도해 주세요.'), type: 'error' })
      if (ruleBroken) onClose()
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      open
      footer={
        <Button onClick={submit} disabled={!canSubmit} loading={busy} loadingText="신고 중...">
          신고
        </Button>
      }
    >
      <header className="report-modal__head">
        <h2 className="report-modal__title">신고</h2>
        <button type="button" className="report-modal__close" aria-label="닫기" onClick={onClose} disabled={busy}>
          ×
        </button>
      </header>

      <div className="pm-field">
        <span className="pm-field__label">사유</span>
        <div className="report-modal__reasons" role="radiogroup" aria-label="사유">
          {REASONS.map((r) => (
            <Button
              key={r.value}
              variant={reason === r.value ? 'primary' : 'secondary'}
              role="radio"
              aria-checked={reason === r.value}
              disabled={busy}
              onClick={() => setReason(r.value)}
            >
              {r.label}
            </Button>
          ))}
        </div>
      </div>

      {/* '기타(직접 입력)'를 고르면 나타남 */}
      {reason === 'ETC' && (
        <div className="pm-field report-modal__detail">
          <label className="pm-field__label" htmlFor={detailId}>
            상세 내용
          </label>
          <textarea
            id={detailId}
            className="pm-field__input report-modal__textarea"
            rows={4}
            value={detail}
            disabled={busy}
            onChange={changeDetail}
          />
          <span className="pm-field__count">
            {countChars(detail)}/{REPORT_DETAIL_MAX}
          </span>
        </div>
      )}
    </Modal>
  )
}
