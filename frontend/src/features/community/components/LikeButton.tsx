// PUB-04-03·05 [좋아요] 글자 버튼 "좋아요 12" (하트 대신 글자, 11/13 결정). 내가 누른 상태면 버튼이 채워짐
// 처리 중(busy)에는 잠금: 모양은 그대로 두고(바로 채워진 모양이 보이게) 누름만 막는다.
import { Button } from '../../../shared/components/Button'

export default function LikeButton({
  count,
  liked,
  disabled,
  busy = false,
  onClick,
}: {
  count: number
  liked: boolean
  disabled: boolean // 이용 제한 회원
  busy?: boolean // 처리 중
  onClick: () => void
}) {
  return (
    <Button
      variant={liked ? 'primary' : 'secondary'}
      aria-pressed={liked}
      aria-disabled={busy || undefined}
      disabled={disabled}
      onClick={() => {
        if (!busy) onClick()
      }}
    >
      좋아요 {count}
    </Button>
  )
}
