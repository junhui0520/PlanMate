// PUB-04-03 [좋아요] 글자 버튼 "좋아요 12" (하트 대신 글자, 11/13 결정). 내가 누른 상태면 버튼이 채워짐
// TODO(D): PUB-05-01·02 좋아요·취소 동작은 그 단계에서 onClick에 붙인다.
import { Button } from '../../../shared/components/Button'

export default function LikeButton({
  count,
  liked,
  disabled,
  onClick,
}: {
  count: number
  liked: boolean
  disabled: boolean // 이용 제한 회원
  onClick: () => void
}) {
  return (
    <Button variant={liked ? 'primary' : 'secondary'} aria-pressed={liked} disabled={disabled} onClick={onClick}>
      좋아요 {count}
    </Button>
  )
}
