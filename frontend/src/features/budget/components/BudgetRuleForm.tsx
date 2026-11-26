// BUD-02-01 예산 기준 계산 창 "인원 / 일수로 계산" (와이어프레임 D-13)
// 기준 금액 x 선택한 값들(여행 인원 / 일수 / 횟수)을 미리 보여 주고, [반영]하면 추가 창 금액 칸을 채운다.
// 정한 내용(11/26): [반영] → 추가 창으로 돌아가 채우고, [저장]할 때 항목이 생김. 금액은 서버가 다시 계산.
import { useEffect, useState } from 'react'
import { Button } from '../../../shared/components/Button'
import { Modal } from '../../../shared/components/Modal'
import { formatMoney } from '../../../shared/utils/money'
import { getExchangeRate, type BudgetRule } from '../api/budgetApi.ts'
import {
  currencyUnitName,
  formatAmountInput,
  MAX_AMOUNT_KRW,
  parseAmountInput,
  roundToCurrency,
} from '../utils/money.ts'

const CURRENCIES = ['KRW', 'JPY', 'USD', 'EUR'] // BUD-01-02와 같은 통화 목록
const DAYS_MAX = 7 // BUD-02-01 일수 1~7
const TIMES_MAX = 99 // BUD-02-01 횟수 1~99

// 숫자 칸: 숫자만, 범위(1~max)를 넘는 입력은 받지 않음(비우는 것은 됨)
function clampCount(text: string, max: number, prev: string): string {
  const digits = text.replace(/[^0-9]/g, '')
  if (digits === '') return ''
  const value = Number(digits)
  return value >= 1 && value <= max ? String(value) : prev
}

export interface AppliedRule {
  rule: BudgetRule
  currency: string
  total: number
  note: string // "30,000원 × 4명 × 3일 = 360,000원"
}

export default function BudgetRuleForm({
  memberCount,
  tripDays,
  initialCurrency,
  onApply,
  onClose,
}: {
  memberCount: number // 여행 인원 (자동)
  tripDays: number // 일수 기본값 = 여행 일수
  initialCurrency: string
  onApply: (applied: AppliedRule) => void
  onClose: () => void
}) {
  const [baseText, setBaseText] = useState('')
  const [currency, setCurrency] = useState(initialCurrency)
  // 피그마 D-13 처음 상태: 여행 인원·일수 체크, 횟수는 체크 안 함
  const [usePeople, setUsePeople] = useState(true)
  const [useDays, setUseDays] = useState(true)
  const [daysText, setDaysText] = useState(String(Math.min(Math.max(tripDays, 1), DAYS_MAX)))
  const [useTimes, setUseTimes] = useState(false)
  const [timesText, setTimesText] = useState('1')

  // 1억(원화 기준) 확인용 오늘 환율 (추가 창과 같은 규칙, 못 불러오면 화면에서는 막지 않음)
  const [rates, setRates] = useState<Record<string, number>>({ KRW: 1 })
  useEffect(() => {
    if (currency === 'KRW') return
    let ignore = false
    getExchangeRate(currency)
      .then((r) => {
        if (!ignore) setRates((prev) => ({ ...prev, [currency]: r.ratePerOne }))
      })
      .catch(() => {})
    return () => {
      ignore = true
    }
  }, [currency])
  const perOne = rates[currency] ?? null

  const base = parseAmountInput(baseText)
  const days = useDays ? (daysText === '' ? null : Number(daysText)) : null
  const times = useTimes ? (timesText === '' ? null : Number(timesText)) : null
  const countsFilled = (!useDays || days !== null) && (!useTimes || times !== null)

  // 기준 금액 x 선택한 값들 (곱할 값이 없으면 기준 금액 그대로, 정한 내용)
  const total =
    base === null ? null : roundToCurrency(base * (usePeople ? memberCount : 1) * (days ?? 1) * (times ?? 1), currency)
  const note =
    base === null || total === null
      ? null
      : [
          formatMoney(base, currency),
          usePeople ? `${memberCount}명` : null,
          useDays && days !== null ? `${days}일` : null,
          useTimes && times !== null ? `${times}회` : null,
        ]
          .filter(Boolean)
          .join(' × ') + ` = ${formatMoney(total, currency)}`
  const overLimit = total !== null && perOne !== null && total * perOne > MAX_AMOUNT_KRW
  // 기준값이 비어 있으면 계산하지 않음([반영] 비활성), 결과가 1억을 넘어도 [반영]만 막음(추가 창과 같음)
  const canApply = base !== null && countsFilled && !overLimit

  const apply = () => {
    if (!canApply || base === null || total === null || note === null) return
    onApply({
      rule: { baseAmount: base, multiplyPeople: usePeople, days: useDays ? days : null, times: useTimes ? times : null },
      currency,
      total,
      note,
    })
  }

  return (
    <Modal
      open
      footer={
        // 버튼은 [반영]만 ([취소] 없음)
        <Button disabled={!canApply} onClick={apply}>
          반영
        </Button>
      }
    >
      <div className="budget-form">
        <header className="budget-modal__head">
          <h2 className="budget-modal__title">인원 / 일수로 계산</h2>
          <button type="button" className="budget-modal__close" aria-label="닫기" onClick={onClose}>
            ×
          </button>
        </header>

        <div className="budget-form__amount-row">
          <label className="budget-form__field">
            <span className="pm-field__label">기준 금액</span>
            <span className="budget-form__amount-input">
              <input
                className="pm-field__input"
                inputMode="decimal"
                value={baseText}
                onChange={(e) => setBaseText(formatAmountInput(e.target.value, currency))}
              />
              <span className="budget-form__amount-unit" aria-hidden="true">
                {currencyUnitName(currency)}
              </span>
            </span>
          </label>
          <label className="budget-form__field">
            <span className="pm-field__label">통화</span>
            <select
              className="pm-field__input"
              value={currency}
              onChange={(e) => {
                setCurrency(e.target.value)
                setBaseText(formatAmountInput(baseText, e.target.value))
              }}
            >
              {[...new Set([initialCurrency, ...CURRENCIES])].map((c) => (
                <option key={c} value={c}>
                  {c} {currencyUnitName(c)}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="budget-form__field">
          <span className="pm-field__label">곱할 값</span>
          {/* 체크박스 여러 개 선택 가능, 한 줄에 하나씩 세로로 */}
          <label className="budget-rule__row">
            <input type="checkbox" checked={usePeople} onChange={(e) => setUsePeople(e.target.checked)} />
            <span>× 여행 인원 ({memberCount}명)</span>
          </label>
          <div className="budget-rule__row">
            <label className="budget-rule__check">
              <input type="checkbox" checked={useDays} onChange={(e) => setUseDays(e.target.checked)} />
              <span>× 일수</span>
            </label>
            <input
              className="pm-field__input budget-rule__count"
              inputMode="numeric"
              aria-label="일수"
              value={daysText}
              disabled={!useDays}
              onChange={(e) => setDaysText((prev) => clampCount(e.target.value, DAYS_MAX, prev))}
            />
          </div>
          <div className="budget-rule__row">
            <label className="budget-rule__check">
              <input type="checkbox" checked={useTimes} onChange={(e) => setUseTimes(e.target.checked)} />
              <span>× 횟수</span>
            </label>
            <input
              className="pm-field__input budget-rule__count"
              inputMode="numeric"
              aria-label="횟수"
              value={timesText}
              disabled={!useTimes}
              onChange={(e) => setTimesText((prev) => clampCount(e.target.value, TIMES_MAX, prev))}
            />
          </div>
        </div>

        {/* 계산 미리보기: "30,000원 × 4명 × 3일 = 360,000원" */}
        {note && countsFilled && <p className="budget-rule__preview">{note}</p>}
      </div>
    </Modal>
  )
}
