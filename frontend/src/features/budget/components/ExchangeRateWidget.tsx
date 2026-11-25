// BUD-04-03 환율 위젯: "[100] 엔 = [910] 원 · 11.24 기준", 어느 칸에 넣어도 반대 칸 계산(저장하지 않음)
import { useEffect, useState } from 'react'
import { getExchangeRate, type ExchangeRate } from '../api/budgetApi.ts'
import { roundToCurrency } from '../utils/money.ts'

// 칸 이름에 쓰는 나라·통화 이름 (없는 통화는 코드로 표시)
const CURRENCY_LABEL: Record<string, { country: string; unitName: string }> = {
  KRW: { country: '대한민국', unitName: '원' },
  JPY: { country: '일본', unitName: '엔' },
  USD: { country: '미국', unitName: '달러' },
  EUR: { country: '유럽연합', unitName: '유로' },
  CNY: { country: '중국', unitName: '위안' },
}

function label(currency: string) {
  return CURRENCY_LABEL[currency] ?? { country: '', unitName: currency }
}

// 입력 칸 숫자 → 통화 표준 자릿수로 반올림한 글자 (BUD-04-02 엔·원 0자리, 달러·유로 2자리)
function toText(value: number, currency: string): string {
  return String(roundToCurrency(value, currency))
}

// 숫자와 소수점만 남김
function cleanNumber(text: string): string {
  return text.replace(/[^0-9.]/g, '').replace(/(\..*)\./g, '$1')
}

// "2026-11-24" → "11.24"
function shortDate(isoDate: string): string {
  const [, month, day] = isoDate.split('-')
  return `${month}.${day}`
}

function RateCalculator({ rate }: { rate: ExchangeRate }) {
  // 처음 값은 오늘 환율 그대로: 100엔 = 910원
  const [foreignText, setForeignText] = useState(String(rate.unit))
  const [krwText, setKrwText] = useState(toText(rate.rate, 'KRW'))
  const foreign = label(rate.currency)
  const krw = label('KRW')

  // 외화 칸에 넣으면 원화 칸 계산
  const onForeignChange = (text: string) => {
    const cleaned = cleanNumber(text)
    setForeignText(cleaned)
    setKrwText(cleaned === '' ? '' : toText(Number(cleaned) * rate.ratePerOne, 'KRW'))
  }

  // 원화 칸에 넣으면 외화 칸 계산
  const onKrwChange = (text: string) => {
    const cleaned = cleanNumber(text)
    setKrwText(cleaned)
    setForeignText(cleaned === '' ? '' : toText(Number(cleaned) / rate.ratePerOne, rate.currency))
  }

  // 와이어프레임 D-11: 외화 칸 위, "=" 가운데, 원화 칸 아래
  return (
    <div className="budget-rate__calc">
      <RateField
        name={`${foreign.country} ${rate.currency}`}
        unitName={foreign.unitName}
        value={foreignText}
        onChange={onForeignChange}
      />
      <span className="budget-rate__equals" aria-hidden="true">
        =
      </span>
      <RateField name={`${krw.country} KRW`} unitName={krw.unitName} value={krwText} onChange={onKrwChange} />
    </div>
  )
}

// 칸 하나: 위에 나라·통화 이름, 왼쪽 큰 숫자 입력, 오른쪽 작은 "100 엔"
function RateField({
  name,
  unitName,
  value,
  onChange,
}: {
  name: string
  unitName: string
  value: string
  onChange: (text: string) => void
}) {
  return (
    <label className="budget-rate__field">
      <span className="budget-rate__field-name">{name}</span>
      <span className="budget-rate__field-row">
        <input
          className="budget-rate__input"
          inputMode="decimal"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          aria-label={`${unitName} 금액`}
        />
        <span className="budget-rate__field-unit">
          {value} {unitName}
        </span>
      </span>
    </label>
  )
}

type RateState = { status: 'loading' } | { status: 'error' } | { status: 'done'; rate: ExchangeRate }

// BUD-04-03 경비 탭에만 표시. 국내 여행(원화)은 위젯을 보여주지 않음(부르는 쪽에서 판단)
export default function ExchangeRateWidget({ currency }: { currency: string }) {
  const [state, setState] = useState<RateState>({ status: 'loading' })

  useEffect(() => {
    let ignore = false
    getExchangeRate(currency)
      .then((rate) => {
        if (!ignore) setState({ status: 'done', rate })
      })
      .catch(() => {
        if (!ignore) setState({ status: 'error' })
      })
    return () => {
      ignore = true
    }
  }, [currency])

  if (state.status === 'loading') return null // 스피너 없이 불러온 뒤 표시

  return (
    <section className="budget-rate" aria-label="환율">
      <h2 className="budget-section__title">환율</h2>
      {state.status === 'error' ? (
        <p className="budget-rate__error">환율 정보를 불러오지 못했어요.</p>
      ) : (
        <>
          {/* key: 통화·환율이 바뀌면 칸을 처음 값으로 다시 채움 */}
          <RateCalculator key={`${state.rate.currency}-${state.rate.baseDate}`} rate={state.rate} />
          <p className="budget-rate__note">
            {shortDate(state.rate.baseDate)} 기준 · 한국수출입은행
            <br />
            환율은 오전 11시 기준으로 변경됩니다.
          </p>
        </>
      )}
    </section>
  )
}
