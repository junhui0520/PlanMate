// PUB-03-02 도시·일수·제목 검색 (와이어프레임 D-01 목록 위 검색 영역)
// [도시 ▼] [일수 ▼] [제목으로 찾기] [검색] 초기화
// 고른 값은 [검색]이나 Enter를 눌러야 목록에 적용된다.
import { useEffect, useState, type FormEvent } from 'react'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { getCityOptions, SEARCH_DAYS_MAX, SEARCH_KEYWORD_MAX, type CityOption } from '../api/communityApi.ts'

export interface SearchConditions {
  q: string // 제목 검색어 (비어 있으면 조건 없음)
  cityId: number | null // null = 전체 도시
  days: number | null // null = 전체 일수
}

const DAY_OPTIONS = Array.from({ length: SEARCH_DAYS_MAX }, (_, i) => i + 1) // 1일 ~ 7일

export default function PostSearchBar({
  applied,
  onSearch,
  onReset,
}: {
  applied: SearchConditions // 지금 목록에 적용된 조건 (주소에서 읽음)
  onSearch: (conditions: SearchConditions) => void
  onReset: () => void
}) {
  // 입력 중인 값 (뒤로가기로 주소가 바뀌면 부모가 key를 바꿔 새로 채움)
  const [q, setQ] = useState(applied.q)
  const [cityId, setCityId] = useState(applied.cityId)
  const [days, setDays] = useState(applied.days)

  // 도시 선택 목록: 처음엔 "전체 도시"만, 불러오면 등록된 모든 도시 (못 불러와도 검색은 됨)
  const [cities, setCities] = useState<CityOption[]>([])
  useEffect(() => {
    let ignore = false
    getCityOptions()
      .then((list) => {
        if (!ignore) setCities(list)
      })
      .catch(() => {})
    return () => {
      ignore = true
    }
  }, [])

  const submit = (e: FormEvent) => {
    e.preventDefault() // [검색] 또는 Enter
    onSearch({ q: q.trim(), cityId, days }) // 공백뿐이면 검색어 없이 전체
  }

  return (
    <form className="post-search" onSubmit={submit} role="search">
      <select
        className="pm-field__input post-search__select"
        aria-label="도시"
        value={cityId ?? ''}
        onChange={(e) => setCityId(e.target.value === '' ? null : Number(e.target.value))}
      >
        <option value="">전체 도시</option>
        {/* 주소에 있던 도시가 아직 목록에 없으면(불러오는 중) 선택이 풀리지 않게 자리만 둠 */}
        {cityId !== null && !cities.some((c) => c.cityId === cityId) && <option value={cityId}>선택한 도시</option>}
        {cities.map((c) => (
          <option key={c.cityId} value={c.cityId}>
            {c.name}
          </option>
        ))}
      </select>
      <select
        className="pm-field__input post-search__select"
        aria-label="일수"
        value={days ?? ''}
        onChange={(e) => setDays(e.target.value === '' ? null : Number(e.target.value))}
      >
        <option value="">전체 (일수)</option>
        {DAY_OPTIONS.map((d) => (
          <option key={d} value={d}>
            {d}일
          </option>
        ))}
      </select>
      <div className="post-search__keyword">
        {/* 50자가 되면 더 입력되지 않음 (공통 Input: 글자 수는 코드 포인트 기준) */}
        <Input
          aria-label="제목 검색어"
          placeholder="제목으로 찾기"
          value={q}
          onValueChange={setQ}
          maxChars={SEARCH_KEYWORD_MAX}
        />
      </div>
      <Button type="submit">검색</Button>
      <Button type="button" variant="text" className="post-search__reset" onClick={onReset}>
        초기화
      </Button>
    </form>
  )
}
