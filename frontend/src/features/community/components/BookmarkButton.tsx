// PUB-04-03·06 북마크: 책갈피 아이콘만 (저장하면 아이콘이 채워짐). 좋아요·보관함·복제와 다른 동작
// 처리 중(busy)에는 잠금: 누름만 막는다.
import { Button } from '../../../shared/components/Button'

export default function BookmarkButton({
  bookmarked,
  disabled,
  busy = false,
  className,
  onClick,
}: {
  bookmarked: boolean
  disabled: boolean // 이용 제한 회원
  busy?: boolean // 처리 중
  className?: string // 내 북마크 카드에서는 테두리 없는 아이콘
  onClick: () => void
}) {
  return (
    <Button
      variant="secondary"
      className={['post-bookmark', className].filter(Boolean).join(' ')}
      aria-label={bookmarked ? '북마크 해제' : '북마크'}
      aria-pressed={bookmarked}
      aria-disabled={busy || undefined}
      disabled={disabled}
      onClick={() => {
        if (!busy) onClick()
      }}
    >
      <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
        <path
          d="M4 1.5h8a.5.5 0 0 1 .5.5v12.2L8 11.3l-4.5 2.9V2a.5.5 0 0 1 .5-.5Z"
          fill={bookmarked ? 'currentColor' : 'none'}
          stroke="currentColor"
          strokeWidth="1.3"
          strokeLinejoin="round"
        />
      </svg>
    </Button>
  )
}
