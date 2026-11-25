// 경비 금액 표시 (BUD-01-01, BUD-03, BUD-04-02)
// TODO(공통): shared/utils/money.ts가 생기면 그쪽으로 옮기고 이 파일은 지운다.
import type { CurrencyAmount } from '../api/budgetApi.ts'

// BUD-04-02 통화 표준 자릿수 (엔·원 0자리, 달러·유로 2자리)
export function currencyDigits(currency: string): number {
  try {
    return (
      new Intl.NumberFormat('ko-KR', { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? 2
    )
  } catch {
    return 2
  }
}

export function roundToCurrency(amount: number, currency: string): number {
  const factor = 10 ** currencyDigits(currency)
  return Math.round(amount * factor) / factor
}

const PREFIX_SYMBOL: Record<string, string> = { JPY: '¥', USD: '$', EUR: '€' }

// BUD-01-01 천 단위 쉼표 + 통화별 자릿수. 예: ¥40,000 / 30,000원
export function formatMoney(amount: number, currency: string): string {
  const digits = currencyDigits(currency)
  const number = amount.toLocaleString('ko-KR', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
  if (currency === 'KRW') return `${number}원`
  const symbol = PREFIX_SYMBOL[currency]
  return symbol ? `${symbol}${number}` : `${number} ${currency}`
}

// BUD-03 통화별 합계 문구 (서버가 준 순서 그대로). 예: ¥13,400 + 82,000원
export function formatCurrencyAmounts(amounts: CurrencyAmount[]): string {
  return amounts.map((a) => formatMoney(a.amount, a.currency)).join(' + ')
}

// BUD-04-02 원화 합산. 예: 약 203,940원
export function formatKrwTotal(krw: number): string {
  return `약 ${formatMoney(krw, 'KRW')}`
}

// ---- 금액 입력 칸 (BUD-01-02) ----

const UNIT_NAME: Record<string, string> = { KRW: '원', JPY: '엔', USD: '달러', EUR: '유로', CNY: '위안' }

// 통화 단위 이름 (없는 통화는 코드). 예: JPY → 엔
export function currencyUnitName(currency: string): string {
  return UNIT_NAME[currency] ?? currency
}

// BUD-01-02 금액 최대 1억 (원화 기준)
export const MAX_AMOUNT_KRW = 100_000_000

/**
 * 금액 칸 글자를 통화 자릿수에 맞게 정리하고 천 단위 쉼표를 붙인다.
 * 숫자·소수점 말고는 지우고, 통화 자릿수를 넘는 소수는 자른다. 예: "30000" → "30,000"
 */
export function formatAmountInput(text: string, currency: string): string {
  const digits = currencyDigits(currency)
  const cleaned = text.replace(/[^0-9.]/g, '')
  const [intRaw, ...rest] = cleaned.split('.')
  const intPart = intRaw.replace(/^0+(?=\d)/, '') // 앞자리 0 정리 ("007" → "7")
  const withComma = intPart === '' ? '' : Number(intPart).toLocaleString('ko-KR')
  if (digits === 0 || rest.length === 0) return withComma
  const decimals = rest.join('').slice(0, digits)
  return `${withComma === '' ? '0' : withComma}.${decimals}`
}

// "30,000.5" → 30000.5, 빈칸이면 null
export function parseAmountInput(text: string): number | null {
  const cleaned = text.replace(/,/g, '')
  if (cleaned === '' || cleaned === '.') return null
  return Number(cleaned)
}
