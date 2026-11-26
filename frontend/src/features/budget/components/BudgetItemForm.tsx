// BUD-01-02 경비 항목 추가 창 · BUD-01-04 수정 창 (와이어프레임 D-12, 같은 창에 지금 값이 들어 있음)
import { useEffect, useRef, useState } from 'react'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { Modal } from '../../../shared/components/Modal'
import { formatMoney } from '../../../shared/utils/money'
import { isBlank, replaceControlChars } from '../../../shared/utils/validation'
import BudgetRuleForm, { type AppliedRule } from './BudgetRuleForm.tsx'
import {
  addBudgetItem,
  getExchangeRate,
  updateBudgetItem,
  type BudgetCategory,
  type BudgetItem,
  type BudgetItemRequest,
  type BudgetScope,
  type CalcType,
} from '../api/budgetApi.ts'
import {
  currencyUnitName,
  formatAmountInput,
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

// BUD-01-02 글자 수는 공통 Input이 countChars(이모지 1자)로 세고, 넘으면 더 입력되지 않음 (maxLength 속성 안 씀)
const NAME_MAX = 50 // 항목명 1~50자
const MEMO_MAX = 200 // 메모 0~200자

type FieldErrors = { name?: string; amount?: string }

export default function BudgetItemForm({
  tripId,
  scope,
  editing,
  tripCurrency,
  memberCount,
  tripDays,
  onClose,
  onSaved,
  onServerError,
}: {
  tripId: number
  scope: BudgetScope // 누른 버튼으로 정해짐 (공통 / 내 개인), 수정이면 그 항목의 구분
  editing?: BudgetItem // BUD-01-04 수정할 항목 (없으면 추가)
  tripCurrency: string
  memberCount: number
  tripDays: number // BUD-02-01 일수 기본값
  onClose: () => void
  onSaved: () => void
  onServerError: (message: string) => void
}) {
  const isCommon = scope === 'COMMON'
  // 추가: 정한 내용대로 분류·계산 방식은 고르지 않은 상태로 시작 (고르기 전에는 [저장] 비활성)
  // 수정: 지금 값이 들어 있는 상태로 시작 (BUD-01-04)
  const [category, setCategory] = useState<BudgetCategory | null>(editing?.category ?? null)
  const [calcType, setCalcType] = useState<CalcType | null>(editing?.calcType ?? null)
  const [name, setName] = useState(editing?.name ?? '')
  const [amountText, setAmountText] = useState(
    editing && editing.amount !== null ? formatAmountInput(String(editing.amount), editing.currency) : '',
  )
  const [currency, setCurrency] = useState(editing?.currency ?? tripCurrency) // 추가 기본값은 여행 도시 통화
  const [priceUnknown, setPriceUnknown] = useState(editing ? editing.amount === null : false)
  const [memo, setMemo] = useState(editing?.memo ?? '')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [saving, setSaving] = useState(false)
  const savingRef = useRef(false) // 같은 요청 다시 보내지 않음
  // BUD-02-01 [인원 / 일수로 계산]으로 채운 금액. 금액·통화·계산 방식·가격 확인 전을 직접 바꾸면 풀림(직접 입력)
  const [rule, setRule] = useState<AppliedRule | null>(null)
  const [ruleOpen, setRuleOpen] = useState(false)
  const canUseRule = isCommon && !editing // 정한 내용(11/26): 공통 경비 추가 창에서만

  const applyRule = (applied: AppliedRule) => {
    setRule(applied)
    setCalcType('TOTAL') // 계산 방식 '전체 금액'
    setCurrency(applied.currency)
    setAmountText(formatAmountInput(String(applied.total), applied.currency))
    setPriceUnknown(false)
    setErrors((e) => ({ ...e, amount: undefined }))
    setRuleOpen(false)
  }

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

  // 금액 칸: 천 단위 쉼표 자동, 통화 자릿수까지만.
  // 원화 1억을 넘게 "늘리는" 입력은 막고, 줄이거나 지우는 입력은 항상 됨(1억이 넘은 금액도 고칠 수 있게)
  const onAmountChange = (text: string) => {
    const formatted = formatAmountInput(text, currency)
    const value = parseAmountInput(formatted)
    const before = parseAmountInput(amountText)
    if (value !== null && overLimit(value, perOne) && (before === null || value > before)) return
    setAmountText(formatted)
    setRule(null)
    setErrors((e) => ({ ...e, amount: undefined }))
  }

  // 통화를 바꾸면 자릿수만 맞춤. 새 통화로 1억을 넘어도 숫자는 그대로 두고 [저장]만 막음
  const onCurrencyChange = (next: string) => {
    setCurrency(next)
    setAmountText(formatAmountInput(amountText, next))
    setRule(null)
  }

  const amount = priceUnknown ? null : parseAmountInput(amountText)
  const amountOverLimit = amount !== null && overLimit(amount, perOne) // 가격 확인 전이면 해당 없음
  const canSave = category !== null && (!isCommon || calcType !== null) && !amountOverLimit

  const save = async () => {
    if (savingRef.current || !canSave) return
    // 칸 아래 오류 (기능정의서 문구 그대로)
    const nextErrors: FieldErrors = {}
    if (isBlank(name)) nextErrors.name = '항목명을 입력해 주세요.'
    if (!priceUnknown && amount === null) nextErrors.amount = "금액을 입력하거나 '가격 확인 전'을 선택해 주세요."
    else if (amount !== null && amount < 0) nextErrors.amount = '0 이상으로 입력해 주세요.'
    setErrors(nextErrors)
    if (nextErrors.name || nextErrors.amount) return

    savingRef.current = true
    setSaving(true)
    try {
      const request: BudgetItemRequest = {
        scope,
        category: category as BudgetCategory,
        name: name.trim(), // 앞뒤 공백 제거
        calcType: isCommon ? calcType : null,
        // BUD-02-01 예산 기준이면 금액은 비워서 보내고 서버가 정해진 산식으로 계산
        amount: rule || amount === null ? null : roundToCurrency(amount, currency),
        priceUnknown: rule ? false : priceUnknown,
        currency,
        memo: isCommon && !isBlank(memo) ? memo.trim() : null,
        budgetRule: rule ? rule.rule : null,
      }
      if (editing) await updateBudgetItem(tripId, editing.itemId, request)
      else await addBudgetItem(tripId, request)
      onSaved() // 성공: 창이 닫히고 목록에 반영 (별도 안내 없음)
    } catch (e) {
      // 개수 초과·권한 없음·이미 삭제됨 등은 화면 아래 상태 창으로 (서버 문구 그대로)
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
    // 공통 Modal은 제목 옆 닫기 버튼이 없어서, D-12 "닫기는 X([취소] 없음)"에 맞게 제목과 X를 안쪽에 직접 둠
    <Modal
      open
      footer={
        // 창 버튼은 [저장]만 ([취소] 없음). 처리 중이면 비활성 + "저장 중..."
        <Button disabled={!canSave} loading={saving} loadingText="저장 중..." onClick={save}>
          저장
        </Button>
      }
    >
      <div className="budget-form">
        <header className="budget-modal__head">
          {/* 정한 내용(11/26): 수정 창 제목은 "공통 경비 수정" / "개인 경비 수정" */}
          <h2 className="budget-modal__title">
            {isCommon ? '공통 경비' : '개인 경비'} {editing ? '수정' : '추가'}
          </h2>
          <button type="button" className="budget-modal__close" aria-label="닫기" onClick={onClose}>
            ×
          </button>
        </header>

        <div className="budget-form__field">
          <span className="pm-field__label">분류</span>
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

        <div className="budget-form__field">
          <Input
            label="항목명"
            value={name}
            maxChars={NAME_MAX}
            showCount
            error={errors.name}
            onValueChange={(v) => {
              setName(replaceControlChars(v)) // 제어문자는 공백 1칸으로 (공통 규칙)
              setErrors((e) => ({ ...e, name: undefined }))
            }}
          />
        </div>

        {isCommon && (
          <div className="budget-form__field">
            <span className="pm-field__label">계산 방식</span>
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
                  onClick={() => {
                    setCalcType(key)
                    if (key !== 'TOTAL') setRule(null) // 예산 기준은 '전체 금액'만
                  }}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="budget-form__field">
          <div className="budget-form__label-row">
            <span className="pm-field__label">금액</span>
            {/* BUD-02-01 금액 칸 위의 [인원 / 일수로 계산] (피그마 D-12 오른쪽 밑줄 글자) */}
            {canUseRule && (
              <Button variant="text" onClick={() => setRuleOpen(true)}>
                인원 / 일수로 계산
              </Button>
            )}
          </div>
          <div className="budget-form__amount-row">
            {/* 칸 안 오른쪽에 지금 통화 단위를 보여 줘서 원·엔을 헷갈리지 않게 함 (예: "30,000 엔") */}
            <span className="budget-form__amount-input">
              <input
                className="pm-field__input"
                inputMode="decimal"
                aria-label="금액"
                aria-invalid={errors.amount ? true : undefined}
                value={priceUnknown ? '' : amountText}
                disabled={priceUnknown}
                onChange={(e) => onAmountChange(e.target.value)}
              />
              <span className="budget-form__amount-unit" aria-hidden="true">
                {currencyUnitName(currency)}
              </span>
            </span>
            <select
              className="pm-field__input"
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
          {perPersonPreview && <p className="pm-field__hint">{perPersonPreview}</p>}
          {/* BUD-02-01 예산 기준으로 채운 금액이면 계산식을 보여 줌 (근거는 저장 후 펼친 칸에서도 볼 수 있음) */}
          {rule && <p className="pm-field__hint">예산 기준: {rule.note}</p>}
          {errors.amount && <p className="pm-field__error">{errors.amount}</p>}
          <label className="budget-form__check">
            <input
              type="checkbox"
              checked={priceUnknown}
              onChange={(e) => {
                setPriceUnknown(e.target.checked)
                setRule(null)
                setErrors((v) => ({ ...v, amount: undefined }))
              }}
            />
            가격 확인 전
          </label>
        </div>

        {isCommon && (
          <div className="budget-form__field">
            <Input
              label="메모 (선택)"
              value={memo}
              maxChars={MEMO_MAX}
              showCount
              onValueChange={(v) => setMemo(replaceControlChars(v))}
            />
          </div>
        )}
      </div>

      {/* BUD-02-01 예산 기준 계산 창 (D-13, 추가 창 위에 뜸) */}
      {ruleOpen && (
        <BudgetRuleForm
          memberCount={memberCount}
          tripDays={tripDays}
          initialCurrency={currency}
          onApply={applyRule}
          onClose={() => setRuleOpen(false)}
        />
      )}
    </Modal>
  )
}
