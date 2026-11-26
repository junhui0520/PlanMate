// PUB-04-01·03 공개 여행 상세 (/community/:postId, 와이어프레임 D-02). 로그인 없이 볼 수 있음
// 위: 제목·작성자·도시·일수·좋아요·게시(수정) 시각·소개 + 오른쪽 버튼(PUB-04-03)
// 가운데: N일차 탭 → 왼쪽 장소 목록(구간 한 줄, 누르면 상세 펼침) / 오른쪽 지도(PUB-04-02)
// TODO(D): 아래 댓글(PUB-08)은 그 단계에서 붙인다.
// TODO(B 일정 화면 부품으로 교체): 일정 부분(N일차 탭·장소 카드·구간 줄)은 B 파트 일정 화면 모양을 따르기로 팀에서 정함(11/26).
//   아래 segmentLine·placeLine·SegmentRow·일차 탭·장소 목록은 임시 모양이다. B 부품이 생기면 바꾸고 더 다듬지 않는다.
import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Loading } from '../../../shared/components/Loading'
import { formatRecordedAt, formatTime } from '../../../shared/utils/date'
import {
  getPublicPostDetail,
  POST_NOT_VISIBLE,
  type PostDetail,
  type PublicPlace,
  type PublicSegment,
  type SegmentMode,
} from '../api/communityApi.ts'
import BookmarkButton from '../components/BookmarkButton.tsx'
import LikeButton from '../components/LikeButton.tsx'
import './PostDetailPage.css'

type LoadState =
  | { status: 'loading' }
  | { status: 'hidden' } // 볼 수 없는 게시물
  | { status: 'error' }
  | { status: 'done'; post: PostDetail }

const MODE_LABEL: Record<SegmentMode, string> = { WALK: '도보', TRANSIT: '대중교통', CAR: '자동차' }

// "3.2km" / "850m"
function formatDistance(meters: number): string {
  return meters >= 1000 ? `${(meters / 1000).toFixed(1)}km` : `${meters}m`
}

// 구간 한 줄: "대중교통 15분(예상) · 3.2km", 이동시간이 없으면 "이동시간 미확인" (RTE-02-02와 같은 모양, 요금 없음)
function segmentLine(seg: PublicSegment): string {
  if (seg.durationMinutes === null) return '이동시간 미확인'
  const time = `${MODE_LABEL[seg.mode]} ${seg.durationMinutes}분${seg.estimated ? '(예상)' : ''}`
  return seg.distanceMeters === null ? time : `${time} · ${formatDistance(seg.distanceMeters)}`
}

// 장소 아래 줄: "카페 · 오전 11:45~오후 12:30 · 45분" (시간은 여행 도시 현지 시각 그대로, 12시간제)
function placeLine(place: PublicPlace): string {
  const time = place.startTime
    ? place.endTime
      ? `${formatTime(place.startTime)}~${formatTime(place.endTime)}`
      : formatTime(place.startTime)
    : null
  return [place.category, time, place.stayMinutes !== null ? `${place.stayMinutes}분` : null].filter(Boolean).join(' · ')
}

// TODO(B 일정 화면 부품으로 교체) 임시 구간 줄
// 장소 사이 구간: 대중교통 안내가 있으면 눌러서 노선·환승·도보 구간을 펼침
function SegmentRow({ segment }: { segment: PublicSegment }) {
  const [open, setOpen] = useState(false)
  const details = [segment.transitSummary, segment.walkSummary].filter((d): d is string => Boolean(d))
  if (details.length === 0) {
    return <p className="post-segment">↓ {segmentLine(segment)}</p>
  }
  return (
    <div className={open ? 'post-segment post-segment--open' : 'post-segment'}>
      <button type="button" className="post-segment__toggle" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        {open ? '' : '↓ '}
        {segmentLine(segment)}
      </button>
      {open && (
        <ul className="post-segment__details">
          {details.map((d) => (
            <li key={d}>{d}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

function PostBody({ post }: { post: PostDetail }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [dayNo, setDayNo] = useState(post.schedule[0]?.dayNo ?? 1) // 처음엔 1일차
  const day = post.schedule.find((d) => d.dayNo === dayNo)
  const { viewer } = post

  // 비회원이 버튼을 누르면 로그인 화면으로. 로그인 후 이 게시물로 돌아오고, 누르려던 동작은 다시 눌러야 함
  // TODO(A에 요청): 로그인 후 돌아갈 주소를 넘기는 방법(A-01). 지금은 state.from으로 넘긴다.
  const requireLogin = (action: () => void) => () => {
    if (!viewer.loggedIn) {
      navigate('/login', { state: { from: `${location.pathname}${location.search}` } })
      return
    }
    action()
  }
  // TODO(D): 각 동작은 그 기능 단계에서 붙인다 (PUB-05 좋아요, PUB-06 북마크, PUB-07 복제, PUB-09 신고, PUB-02-05 수정, PUB-02-02 비공개)
  const notYet = () => {}
  const locked = viewer.loggedIn && !viewer.communityWritable // 이용 제한 회원: 버튼 모두 비활성

  // "지은 · 도쿄 · 4일 · 좋아요 12 · 게시 10.20 · 수정 10.28" (고친 적 없으면 게시 시각만)
  const meta = [
    post.authorNickname,
    post.cityName,
    `${post.days}일`,
    `좋아요 ${post.likeCount}`,
    `게시 ${formatRecordedAt(post.publishedAt)}`,
    post.updatedAt ? `수정 ${formatRecordedAt(post.updatedAt)}` : null,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <>
      <header className="post-detail__head">
        <div className="post-detail__heading">
          <h1 className="post-detail__title">{post.title}</h1>
          <p className="post-detail__meta">{meta}</p>
        </div>
        {/* PUB-04-03 버튼: 내 게시물이면 [좋아요]·[제목·소개 수정]·[비공개 전환]만 */}
        <div className="post-detail__actions">
          <LikeButton count={post.likeCount} liked={viewer.liked} disabled={locked} onClick={requireLogin(notYet)} />
          {viewer.mine ? (
            <>
              <Button variant="secondary" disabled={locked} onClick={notYet}>
                제목·소개 수정
              </Button>
              <Button variant="secondary" disabled={locked} onClick={notYet}>
                비공개 전환
              </Button>
            </>
          ) : (
            <>
              <BookmarkButton bookmarked={viewer.bookmarked} disabled={locked} onClick={requireLogin(notYet)} />
              <Button disabled={locked} onClick={requireLogin(notYet)}>
                내 여행으로 복제
              </Button>
              <Button variant="secondary" disabled={locked} onClick={requireLogin(notYet)}>
                신고
              </Button>
            </>
          )}
        </div>
      </header>
      {post.intro && <p className="post-detail__intro">{post.intro}</p>}

      {/* TODO(B 일정 화면 부품으로 교체): N일차 탭·장소 카드·구간 줄은 임시 모양 */}
      {/* N일차 탭 (실제 날짜는 보이지 않음) */}
      <div className="post-detail__days" role="tablist" aria-label="일차">
        {post.schedule.map((d) => (
          <button
            key={d.dayNo}
            type="button"
            role="tab"
            aria-selected={d.dayNo === dayNo}
            className={d.dayNo === dayNo ? 'post-day-tab post-day-tab--active' : 'post-day-tab'}
            onClick={() => setDayNo(d.dayNo)}
          >
            {d.dayNo}일차
          </button>
        ))}
      </div>

      <div className="post-detail__body">
        <ol className="post-places" aria-label={`${dayNo}일차 장소`}>
          {day?.places.map((place) => (
            <li key={place.order}>
              <div className="post-place">
                <span className="post-place__no">{place.order}</span>
                <div>
                  <p className="post-place__name">{place.name}</p>
                  <p className="post-place__sub">{placeLine(place)}</p>
                </div>
              </div>
              {place.segmentToNext && <SegmentRow segment={place.segmentToNext} />}
            </li>
          ))}
        </ol>
        {/* PUB-04-02 지도: C의 MapView(VIEW 모드)로 순서 번호 핀 + 방문 순서 직선. 좌표 없는 장소는 핀만 뺌
            TODO(C에 요청): features/place/components/MapView.tsx가 생기면 이 자리에 넣는다 (경로 API 호출 없음) */}
        <div className="post-map" aria-label="지도" />
      </div>
    </>
  )
}

export default function PostDetailPage() {
  const { postId } = useParams()
  // 다른 게시물로 옮기면 처음부터 다시 불러옴(불러오는 중 → 1일차)
  return <PostDetailLoader key={postId} postIdParam={postId} />
}

function PostDetailLoader({ postIdParam }: { postIdParam: string | undefined }) {
  const navigate = useNavigate()
  const postId = Number(postIdParam)
  const validId = Number.isInteger(postId) && postId > 0
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let ignore = false // 화면을 떠났거나 다시 불러오면 이전 응답은 버림
    if (!validId) return
    getPublicPostDetail(postId)
      .then((post) => {
        if (!ignore) setState({ status: 'done', post })
      })
      .catch((e: unknown) => {
        if (ignore) return
        setState(e instanceof ApiError && e.code === POST_NOT_VISIBLE ? { status: 'hidden' } : { status: 'error' })
      })
    return () => {
      ignore = true
    }
  }, [postId, validId, reloadKey])

  const retry = () => {
    setState({ status: 'loading' })
    setReloadKey((k) => k + 1)
  }

  // 비공개·관리자 숨김·원래 여행 휴지통·없는 게시물(주소가 이상한 경우 포함): 내용 없이 안내 화면
  if (!validId || state.status === 'hidden') {
    return (
      <main className="post-detail">
        <EmptyState
          message="볼 수 없는 게시물이에요."
          action={
            <Button variant="secondary" onClick={() => navigate('/community')}>
              커뮤니티 목록으로
            </Button>
          }
        />
      </main>
    )
  }

  return (
    <main className="post-detail">
      {/* 불러오는 중: 장소 목록 자리에 빈 카드 3개(스켈레톤), 지도 자리는 빈 영역 */}
      {state.status === 'loading' && (
        <div className="post-detail__body">
          <Loading count={3} />
          <div className="post-map" aria-label="지도" />
        </div>
      )}
      {state.status === 'error' && (
        <EmptyState
          message="게시물을 불러오지 못했어요. 다시 시도해 주세요."
          action={
            <Button variant="secondary" onClick={retry}>
              다시 시도
            </Button>
          }
        />
      )}
      {state.status === 'done' && <PostBody post={state.post} />}
    </main>
  )
}
