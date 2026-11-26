// PUB-04-01·02, PUB-01-02 공개 일정: N일차 탭 → 왼쪽 장소 목록(구간 한 줄, 누르면 상세 펼침) / 오른쪽 지도
// 공개 상세(D-02)와 공개 미리보기(D-08)가 같은 모양을 쓴다.
// TODO(B 일정 화면 부품으로 교체): 일정 부분(N일차 탭·장소 카드·구간 줄)은 B 파트 일정 화면 모양을 따르기로 팀에서 정함(11/26).
//   아래 segmentLine·placeLine·SegmentRow·일차 탭·장소 목록은 임시 모양이다. B 부품이 생기면 바꾸고 더 다듬지 않는다.
import { useState } from 'react'
import { formatTime } from '../../../shared/utils/date'
import type { PublicDay, PublicPlace, PublicSegment, SegmentMode } from '../api/communityApi.ts'
import './PublicSchedule.css'

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

export default function PublicSchedule({ schedule }: { schedule: PublicDay[] }) {
  const [dayNo, setDayNo] = useState(schedule[0]?.dayNo ?? 1) // 처음엔 1일차
  const day = schedule.find((d) => d.dayNo === dayNo)

  return (
    <>
      {/* N일차 탭 (실제 날짜는 보이지 않음) */}
      <div className="post-detail__days" role="tablist" aria-label="일차">
        {schedule.map((d) => (
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
