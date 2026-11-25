// BUD-01-02 경비 항목 추가 창 (와이어프레임 D-12). 수정(BUD-01-04)도 나중에 같은 창을 쓴다.
import { useEffect, useRef, useState } from 'react'
import {
  addBudgetItem,
  getExchangeRate,
  type BudgetCategory,
  type BudgetScope,
  type CalcType,
} from '../api/budgetApi.ts'
import {
  currencyUnitName,
  formatAmountInput,
  formatMoney,
  MAX_AMOUNT_KRW,
  parseAmountInput,
  roundToCurrency,
} from '../utils/money.ts'

const CATEGORIES: { key: BudgetCategory; label: string }[] = [
  { key: 'TRANSPORT', label: '교통' },
  { key: 'LODGING', label: '숙박' },
  { key: 'FOOD', label: '식비' },
  { key: 'SIGHTSEEING', label: '관광' },
  { key: 'ETC', label: '기타' },
]

// BUD-01-02 통화 선택 목록 (환율 API가 지원하는 통화)
const CURRENCIES = ['KRW', 'JPY', 'USD', 'EUR']

const NAME_MAX = 50 // BUD-01-02 항목명 1~50자
const MEMO_MAX = 200 // BUD-01-02 메모 0~200자

// BUD-01-02 제어문자 불가 (TRP-01-01 규칙과 동일): 입력할 때 지움
function stripControl(text: string): string {
  return [...text].filter((ch) => {
    const code = ch.charCodeAt(0)
    return code >= 0x20 && code !== 0x7f
  }).join('')
}

type FieldErrors = { name?: string; amount?: string }

export default function BudgetItemForm({
  tripId,
  scope,
  tripCurrency,
  memberCount,
  onClose,
  onSaved,
  onServerError,
}: {
  tripId: number
  scope: BudgetScope // 누른 버튼으로 정해짐 (공통 / 내 개인)
  tripCurrency: string
  memberCount: number
  onClose: () => void
  onSaved: () => void
  onServerError: (message: string) => void
}) {
  const isCommon = scope === 'COMMON'
  // 정한 내용: 처음엔 분류·계산 방식을 고르지 않은 상태 (고르기 전에는 [저장] 비활성)
  const [category, setCategory] = useState<BudgetCategory | null>(null)
  const [calcType, setCalcType] = useState<CalcType | null>(null)
  const [name, setName] = useState('')
  const [amountText, setAmountText] = useState('')
  const [currency, setCurrency] = useState(tripCurrency) // 기본값 여행 도시 통화
  const [priceUnknown, setPriceUnknown] = useState(false)
  const [memo, setMemo] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [saving, setSaving] = useState(false)
  const savingRef = useRef(false) // 같은 요청 다시 보내지 않음
  const nameRef = useRef<HTMLInputElement>(null)

  const overLimit = (amount: number, rate: number | null) => rate !== null && amount * rate > MAX_AMOUNT_KRW

  // 1억(원화 기준) 확인에 쓰는 통화별 오늘 환율(1단위당 원화). 못 불러오면 화면에서는 막지 않음(서버가 다시 검사)
  const [rates, setRates] = useState<Record<string, number>>({ KRW: 1 })
  useEffect(() => {
    if (currency === 'KRW') return
    let ignore = false
    getExchangeRate(currency)
      .then((r) => {
        // 환율이 늦게 와서 이미 쓴 금액이 1억을 넘어도 숫자는 그대로 두고 [저장]만 막음
        if (!ignore) setRates((prev) => ({ ...prev, [currency]: r.ratePerOne }))
      })
      .catch(() => {})
    return () => {
      ignore = true
    }
  }, [currency])
  const perOne = rates[currency] ?? null

  useEffect(() => {
    nameRef.current?.focus()
  }, [])

  // 금액 칸: 천 단위 쉼표 자동, 통화 자릿수까지만.
  // 원화 1억을 넘게 "늘리는" 입력은 막고, 줄이거나 지우는 입력은 항상 됨(1억이 넘은 금액도 고칠 수 있게)
  const onAmountChange = (text: string) => {
    const formatted = formatAmountInput(text, currency)
    const value = parseAmountInput(formatted)
    const before = parseAmountInput(amountText)
    if (value !== null && overLimit(value, perOne) && (before === null || value > before)) return
    setAmountText(formatted)
    setErrors((e) => ({ ...e, amount: undefined }))
  }

  // 통화를 바꾸면 자릿수만 맞춤. 새 통화로 1억을 넘어도 숫자는 그대로 두고 [저장]만 막음
  const onCurrencyChange = (next: string) => {
    setCurrency(next)
    setAmountText(formatAmountInput(amountText, next))
  }

  const amount = priceUnknown ? null : parseAmountInput(amountText)
  const amountOverLimit = amount !== null && overLimit(amount, perOne) // 가격 확인 전이면 해당 없음
  const canSave = category !== null && (!isCommon || calcType !== null) && !amountOverLimit && !saving

  const save = async () => {
    if (savingRef.current || !canSave) return
    // 칸 아래 오류 (기능정의서 문구 그대로)
    const trimmedName = name.trim()
    const nextErrors: FieldErrors = {}
    if (trimmedName === '') nextErrors.name = '항목명을 입력해 주세요.'
    if (!priceUnknown && amount === null) nextErrors.amount = "금액을 입력하거나 '가격 확인 전'을 선택해 주세요."
    else if (amount !== null && amount < 0) nextErrors.amount = '0 이상으로 입력해 주세요.'
    setErrors(nextErrors)
    if (nextErrors.name || nextErrors.amount) return

    savingRef.current = true
    setSaving(true)
    try {
      await addBudgetItem(tripId, {
        scope,
        category: category as BudgetCategory,
        name: trimmedName,
        calcType: isCommon ? calcType : null,
        amount: amount === null ? null : roundToCurrency(amount, currency),
        currency,
        memo: isCommon && memo.trim() !== '' ? memo.trim() : null,
      })
      onSaved() // 성공: 창이 닫히고 목록에 추가 (별도 안내 없음)
    } catch (e) {
      // 개수 초과·권한 없음 등은 화면 아래 상태 창으로
      onServerError(e instanceof Error ? e.message : '')
      savingRef.current = false
      setSaving(false)
    }
  }

  // BUD-01-02 1인당이면 "× 여행 인원 4명 = 120,000원" 미리 표시
  const perPersonPreview =
    isCommon && calcType === 'PER_PERSON' && amount !== null
      ? `× 여행 인원 ${memberCount}명 = ${formatMoney(roundToCurrency(amount * memberCount, currency), currency)}`
      : null

  return (
    // TODO(공통): shared/components/Modal.tsx가 생기면 바꾼다.
    <div className="budget-modal__backdrop">
      <div className="budget-modal" role="dialog" aria-modal="true" aria-labelledby="budget-form-title">
        <header className="budget-modal__head">
          <h2 id="budget-form-title" className="budget-modal__title">
            {isCommon ? '공통 경비 추가' : '개인 경비 추가'}
          </h2>
          <button type="button" className="budget-modal__close" aria-label="닫기" onClick={onClose}>
            ×
          </button>
        </header>

        <div className="budget-form__field">
          <span className="budget-form__label">분류</span>
          <div className="budget-form__choices">
            {CATEGORIES.map((c) => (
              <button
                key={c.key}
                type="button"
                aria-pressed={category === c.key}
                className={category === c.key ? 'budget-choice budget-choice--on' : 'budget-choice'}
                onClick={() => setCategory(c.key)}
              >
                {c.label}
              </button>
            ))}
          </div>
        </div>

        <label className="budget-form__field">
          <span className="budget-form__label">항목명</span>
          <input
            ref={nameRef}
            className="budget-form__input"
            value={name}
            maxLength={NAME_MAX}
            onChange={(e) => {
              setName(stripControl(e.target.value).slice(0, NAME_MAX))
              setErrors((v) => ({ ...v, name: undefined }))
            }}
          />
          <span className="budget-form__foot">
            <span className="budget-form__error">{errors.name}</span>
            <span>
              {name.length}/{NAME_MAX}
            </span>
          </span>
        </label>

        {isCommon && (
          <div className="budget-form__field">
            <span className="budget-form__label">계산 방식</span>
            <div className="budget-form__choices">
              {(
                [
                  ['TOTAL', '전체 금액'],
                  ['PER_PERSON', '1인당 금액'],
                ] as const
              ).map(([key, label]) => (
                <button
                  key={key}
                  type="button"
                  aria-pressed={calcType === key}
                  className={calcType === key ? 'budget-choice budget-choice--on' : 'budget-choice'}
                  onClick={() => setCalcType(key)}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="budget-form__field">
          <span className="budget-form__label">금액</span>
          {/* TODO(D): BUD-02-01 "인원 / 일수로 계산" (D-13) */}
          <div className="budget-form__amount-row">
            {/* 칸 안 오른쪽에 지금 통화 단위를 보여 줘서 원·엔을 헷갈리지 않게 함 (예: "30,000 엔") */}
            <span className="budget-form__amount-input">
              <input
                className="budget-form__input"
                inputMode="decimal"
                aria-label="금액"
                value={priceUnknown ? '' : amountText}
                disabled={priceUnknown}
                onChange={(e) => onAmountChange(e.target.value)}
              />
              <span className="budget-form__amount-unit" aria-hidden="true">
                {currencyUnitName(currency)}
              </span>
            </span>
            <select
              className="budget-form__select"
              aria-label="통화"
              value={currency}
              onChange={(e) => onCurrencyChange(e.target.value)}
            >
              {[...new Set([tripCurrency, ...CURRENCIES])].map((c) => (
                <option key={c} value={c}>
                  {c} {currencyUnitName(c)}
                </option>
              ))}
            </select>
          </div>
          {perPersonPreview && <span className="budget-form__hint">{perPersonPreview}</span>}
          {errors.amount && <span className="budget-form__error">{errors.amount}</span>}
          <label className="budget-form__check">
            <input
              type="checkbox"
              checked={priceUnknown}
              onChange={(e) => {
                setPriceUnknown(e.target.checked)
                setErrors((v) => ({ ...v, amount: undefined }))
              }}
            />
            가격 확인 전
          </label>
        </div>

        {isCommon && (
          <label className="budget-form__field">
            <span className="budget-form__label">메모 (선택)</span>
            <input
              className="budget-form__input"
              value={memo}
              maxLength={MEMO_MAX}
              onChange={(e) => setMemo(stripControl(e.target.value).slice(0, MEMO_MAX))}
            />
            <span className="budget-form__foot">
              <span />
              <span>
                {memo.length}/{MEMO_MAX}
              </span>
            </span>
          </label>
        )}

        <div className="budget-modal__actions">
          {/* 창 버튼은 [저장]만 ([취소] 없음) */}
          <button type="button" className="budget-primary-button" disabled={!canSave} onClick={save}>
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>
    </div>
  )
}
