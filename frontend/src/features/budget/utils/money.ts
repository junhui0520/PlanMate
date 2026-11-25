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
