/*
 * 금액·통화 표시 형식 (기준값 '통화 표시 자릿수', RTE-03-01 천 단위 쉼표).
 * '약 ', '= ' 같은 앞뒤 글자는 각 기능이 붙인다.
 */

// 원·엔은 소수 0자리
const NO_DECIMAL = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });

/**
 * KRW -> "1,500원", JPY -> "¥210", USD·EUR -> 소수 2자리, 그 밖 -> Intl 기본 형식.
 * 원·엔 외 통화 기호는 D 파트 확인 전까지 임시(Intl ko-KR 기본).
 */
export function formatMoney(amount: number, currency: string): string {
  if (currency === 'KRW') return `${NO_DECIMAL.format(amount)}원`;
  if (currency === 'JPY') return `¥${NO_DECIMAL.format(amount)}`;
  if (currency === 'USD' || currency === 'EUR') {
    return new Intl.NumberFormat('ko-KR', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  }
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency }).format(amount);
}
