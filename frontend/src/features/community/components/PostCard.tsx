// PUB-03-01 공개 여행 카드 한 개 (와이어프레임 D-01)
// 위: 대표 사진(없으면 기본 배경) / 제목(최대 2줄) / "도쿄 · 3일" / "지은 · 좋아요 12 · 수정 10.28"
import { Link } from 'react-router'
import { formatRecordedAt } from '../../../shared/utils/date'
import type { PostListItem } from '../api/communityApi.ts'

export default function PostCard({ post }: { post: PostListItem }) {
  const meta = [
    post.authorNickname, // 작성자 정보가 없으면 빼고 표시
    `좋아요 ${post.likeCount}`,
    // 제목·소개·사진을 고쳤으면 끝에 "수정 10.28" (기록 시각 규칙: 오늘이면 "수정 오후 02:05")
    post.updatedAt ? `수정 ${formatRecordedAt(post.updatedAt)}` : null,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    // 카드를 누르면 공개 상세(PUB-04-01). 내 게시물이면 상세에서 작성자 버튼으로 보임(PUB-04-03)
    <Link to={`/community/${post.postId}`} className="post-card">
      {post.imageUrl ? (
        <img className="post-card__image" src={post.imageUrl} alt="" />
      ) : (
        <div className="post-card__image post-card__image--default" aria-hidden="true" />
      )}
      <div className="post-card__body">
        <h3 className="post-card__title">{post.title}</h3>
        <p className="post-card__sub">
          {post.cityName} · {post.days}일
        </p>
        <p className="post-card__meta">{meta}</p>
      </div>
    </Link>
  )
}
