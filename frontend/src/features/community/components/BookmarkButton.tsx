// PUB-04-03 북마크: 책갈피 아이콘만 (저장하면 아이콘이 채워짐). 좋아요·보관함·복제와 다른 동작
// TODO(D): PUB-06-01 북마크 저장·해제 동작은 그 단계에서 onClick에 붙인다.
import { Button } from '../../../shared/components/Button'

export default function BookmarkButton({
  bookmarked,
  disabled,
  onClick,
}: {
  bookmarked: boolean
  disabled: boolean // 이용 제한 회원
  onClick: () => void
}) {
  return (
    <Button
      variant="secondary"
      className="post-bookmark"
      aria-label="북마크"
      aria-pressed={bookmarked}
      disabled={disabled}
      onClick={onClick}
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
