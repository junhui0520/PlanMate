/*
 * 금액·통화 표시 형식 (기준값 '통화 표시 자릿수', RTE-03-01 천 단위 쉼표).
 * '약 ', '= ' 같은 앞뒤 글자는 각 기능이 붙인다.
 */

// 원·엔은 소수 0자리
const NO_DECIMAL = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });
// 달러·유로는 소수 2자리 고정
const TWO_DECIMALS = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * KRW -> "1,500원", JPY -> "¥210", USD -> "$12.50", EUR -> "€12.50", 그 밖 -> Intl 기본 형식.
 * 달러·유로 기호는 D 파트 확인(11/26).
 */
export function formatMoney(amount: number, currency: string): string {
  if (currency === 'KRW') return `${NO_DECIMAL.format(amount)}원`;
  if (currency === 'JPY') return `¥${NO_DECIMAL.format(amount)}`;
  if (currency === 'USD' || currency === 'EUR') {
    const symbol = currency === 'USD' ? '$' : '€';
    return `${symbol}${TWO_DECIMALS.format(amount)}`;
  }
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency }).format(amount);
}
