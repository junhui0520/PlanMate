// PUB-01-01·PUB-02-05 공개 정보 입력 칸 (와이어프레임 D-07). 공유하기 창과 제목·소개 수정 창이 함께 쓴다.
// - 공개용 제목: 한 줄, "커뮤니티에 보일 제목", 0/40 (40자에서 더 입력되지 않음)
// - 소개: 여러 줄(처음 4줄, 최대 10줄까지 늘어남), "어떤 여행인지 소개해 주세요", 0/100, 아래 "개인정보는 적지 말아 주세요"
// - 제어문자는 공백 1칸으로 바꾼다(붙여넣기 포함). 줄바꿈은 소개에서만 허용.
// TODO(팀 결정): 사진(최대 1장)은 형식·크기·저장 위치가 정해지면 [사진 추가]를 붙인다(지금은 기본 배경).
import { useId, useLayoutEffect, useRef, type ChangeEvent } from 'react'
import { Input } from '../../../shared/components/Input'
import { countChars, replaceControlChars, sliceChars } from '../../../shared/utils/validation'
import { INTRO_MAX_ROWS, INTRO_MIN_ROWS, POST_INTRO_MAX, POST_TITLE_MAX, type PostInfo } from '../utils/postInfo.ts'
import './PostInfoForm.css'

// 소개: 줄바꿈만 남기고 다른 제어문자는 공백 1칸, 100자에서 자름
function cleanIntro(value: string): string {
  const lines = value.replace(/\r\n?/g, '\n').split('\n').map(replaceControlChars)
  return sliceChars(lines.join('\n'), POST_INTRO_MAX)
}

export default function PostInfoForm({
  value,
  onChange,
  titleError,
}: {
  value: PostInfo
  onChange: (next: PostInfo) => void
  titleError?: string // 서버가 "공개용 제목을 입력해 주세요."를 돌려줬을 때
}) {
  const introId = useId()
  const introRef = useRef<HTMLTextAreaElement>(null)

  // 소개 칸 높이: 처음 4줄, 글이 길어지면 최대 10줄까지 늘어남
  useLayoutEffect(() => {
    const el = introRef.current
    if (!el) return
    const cs = getComputedStyle(el)
    const lineHeight = parseFloat(cs.lineHeight) || 21
    const border = parseFloat(cs.borderTopWidth) + parseFloat(cs.borderBottomWidth)
    const padding = parseFloat(cs.paddingTop) + parseFloat(cs.paddingBottom)
    el.style.height = 'auto'
    const min = lineHeight * INTRO_MIN_ROWS + padding + border
    const max = lineHeight * INTRO_MAX_ROWS + padding + border
    el.style.height = `${Math.min(max, Math.max(min, el.scrollHeight + border))}px`
  }, [value.intro])

  return (
    <div className="post-info-form">
      <Input
        label="공개용 제목"
        placeholder="커뮤니티에 보일 제목"
        value={value.title}
        onValueChange={(title) => onChange({ ...value, title: replaceControlChars(title) })}
        maxChars={POST_TITLE_MAX}
        showCount
        error={titleError}
      />
      <div className="pm-field">
        <label className="pm-field__label" htmlFor={introId}>
          소개
        </label>
        <textarea
          id={introId}
          ref={introRef}
          className="pm-field__input post-info-form__intro"
          placeholder="어떤 여행인지 소개해 주세요"
          rows={INTRO_MIN_ROWS}
          value={value.intro}
          onChange={(e: ChangeEvent<HTMLTextAreaElement>) => onChange({ ...value, intro: cleanIntro(e.target.value) })}
        />
        <span className="pm-field__count">
          {countChars(value.intro)}/{POST_INTRO_MAX}
        </span>
      </div>
      <p className="post-info-form__notice">
        <span className="post-info-form__icon" aria-hidden="true">
          i
        </span>
        개인정보는 적지 말아 주세요
      </p>
    </div>
  )
}
