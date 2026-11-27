// PUB-08-01·03 댓글 입력칸 (와이어프레임 D-02 아래). 새 댓글과 본인 댓글 수정이 함께 쓴다.
// - 여러 줄(처음 2줄, 최대 6줄까지 늘어남), "댓글을 남겨 보세요", 글자 수 "0/500"
// - 500자가 되면 더 입력되지 않음. 붙여넣기로 넘으면 앞부분만 들어가고 "500자까지만 입력돼요." 안내
// - 줄바꿈 말고 다른 제어문자는 공백 1칸으로 바꿈
// - [등록]은 내용이 있을 때만, 수정 [저장]은 내용이 바뀌었을 때만 활성. 처리 중 "등록 중..." / "저장 중..."
import { useId, useLayoutEffect, useRef, useState, type ChangeEvent } from 'react'
import { Button } from '../../../shared/components/Button'
import { countChars, isBlank, replaceControlChars, sliceChars } from '../../../shared/utils/validation'
import { COMMENT_MAX } from '../api/communityApi.ts'

const MIN_ROWS = 2
const MAX_ROWS = 6

export default function CommentForm({
  initial = '',
  submitLabel,
  loadingLabel,
  onSubmit,
  onCancel,
}: {
  initial?: string // 수정이면 지금 내용
  submitLabel: string // [등록] / [저장]
  loadingLabel: string // "등록 중..." / "저장 중..."
  onSubmit: (content: string) => Promise<boolean> // 성공하면 true (새 댓글이면 입력칸을 비움)
  onCancel?: () => void // 수정일 때만 [취소]
}) {
  const id = useId()
  const ref = useRef<HTMLTextAreaElement>(null)
  const [value, setValue] = useState(initial)
  const [overflow, setOverflow] = useState(false)
  const [busy, setBusy] = useState(false)
  const editing = onCancel !== undefined
  const canSubmit = !isBlank(value) && (!editing || value.trim() !== initial.trim())

  // 높이: 처음 2줄, 길어지면 최대 6줄
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const cs = getComputedStyle(el)
    const lineHeight = parseFloat(cs.lineHeight) || 21
    const border = parseFloat(cs.borderTopWidth) + parseFloat(cs.borderBottomWidth)
    const padding = parseFloat(cs.paddingTop) + parseFloat(cs.paddingBottom)
    el.style.height = 'auto'
    const min = lineHeight * MIN_ROWS + padding + border
    const max = lineHeight * MAX_ROWS + padding + border
    el.style.height = `${Math.min(max, Math.max(min, el.scrollHeight + border))}px`
  }, [value])

  const change = (e: ChangeEvent<HTMLTextAreaElement>) => {
    const lines = e.target.value.replace(/\r\n?/g, '\n').split('\n').map(replaceControlChars)
    const next = lines.join('\n')
    setOverflow(countChars(next) > COMMENT_MAX)
    setValue(sliceChars(next, COMMENT_MAX))
  }

  const submit = async () => {
    if (!canSubmit || busy) return
    setBusy(true)
    const ok = await onSubmit(value)
    setBusy(false)
    if (ok && !editing) {
      setValue('') // 새 댓글: 성공하면 입력칸 비움
      setOverflow(false)
    }
  }

  return (
    <div className="comment-form">
      <textarea
        id={id}
        ref={ref}
        className="pm-field__input comment-form__input"
        aria-label={editing ? '댓글 수정' : '댓글 입력'}
        placeholder="댓글을 남겨 보세요"
        rows={MIN_ROWS}
        value={value}
        onChange={change}
      />
      <div className="comment-form__foot">
        <span className="comment-form__hint">{overflow ? `${COMMENT_MAX}자까지만 입력돼요.` : ''}</span>
        <span className="pm-field__count">
          {countChars(value)}/{COMMENT_MAX}
        </span>
      </div>
      <div className="comment-form__actions">
        {editing && (
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            취소
          </Button>
        )}
        <Button onClick={submit} disabled={!canSubmit} loading={busy} loadingText={loadingLabel}>
          {submitLabel}
        </Button>
      </div>
    </div>
  )
}
