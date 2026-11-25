// 경비 화면 가짜 데이터 (와이어프레임 D-11 예시). 가짜 데이터는 모두 이 파일에만 둔다.
// TODO(D): 서버 API가 생기면 budgetApi.ts가 서버를 부르게 바꾸고 이 파일은 지운다.
//
// 미리보기에서 상태 바꿔 보기: 주소 끝에
//   ?mock=loading | empty | error   (경비 화면 전체)
//   ?mock=rate-error                (환율 위젯만 실패)
//   ?currency=KRW | USD | EUR       (여행 도시 통화 바꾸기, KRW면 위젯 숨김. 기본 JPY)
import type { BudgetListResponse, BudgetSummaryResponse, ExchangeRate } from './api/budgetApi.ts'

// ---- BUD-01-01 경비 목록 ----

const MOCK_BUDGET_LIST: BudgetListResponse = {
  tripId: 1,
  tripCurrency: 'JPY', // 도쿄 여행
  memberCount: 4,
  myMemberId: 1,
  myRole: 'OWNER',
  participants: [
    { memberId: 1, nickname: '지은' },
    { memberId: 2, nickname: '민수' },
    { memberId: 3, nickname: '하늘' },
    { memberId: 4, nickname: '유진' },
  ],
  items: [
    { itemId: 1, scope: 'COMMON', category: 'TRANSPORT', name: '1일차 교통비', calcType: 'PER_PERSON', amount: 400, currency: 'JPY', amountSource: 'ROUTE_FARE', memo: null, createdBy: 1 },
    { itemId: 2, scope: 'COMMON', category: 'LODGING', name: '숙소', calcType: 'TOTAL', amount: 40000, currency: 'JPY', amountSource: 'MANUAL', memo: '3박, 조식 포함', createdBy: 1 },
    { itemId: 3, scope: 'COMMON', category: 'FOOD', name: '저녁 식사', calcType: 'PER_PERSON', amount: 30000, currency: 'KRW', amountSource: 'MANUAL', memo: null, createdBy: 2 },
    { itemId: 4, scope: 'COMMON', category: 'SIGHTSEEING', name: '입장권', calcType: 'TOTAL', amount: null, currency: 'JPY', amountSource: 'MANUAL', memo: null, createdBy: 3 },
    { itemId: 5, scope: 'PERSONAL', category: 'ETC', name: '기념품', calcType: null, amount: 3000, currency: 'JPY', amountSource: 'MANUAL', memo: null, createdBy: 1 },
    { itemId: 6, scope: 'PERSONAL', category: 'TRANSPORT', name: '공항 리무진', calcType: null, amount: 52000, currency: 'KRW', amountSource: 'MANUAL', memo: null, createdBy: 1 },
    { itemId: 7, scope: 'PERSONAL', category: 'ETC', name: '유심', calcType: null, amount: 20000, currency: 'KRW', amountSource: 'MANUAL', memo: null, createdBy: 2 },
    { itemId: 8, scope: 'PERSONAL', category: 'TRANSPORT', name: '무료 셔틀버스', calcType: null, amount: 0, currency: 'KRW', amountSource: 'MANUAL', memo: null, createdBy: 2 },
    { itemId: 9, scope: 'PERSONAL', category: 'FOOD', name: '편의점 간식', calcType: null, amount: 1200, currency: 'JPY', amountSource: 'MANUAL', memo: null, createdBy: 3 },
    { itemId: 10, scope: 'PERSONAL', category: 'ETC', name: '면세점 화장품', calcType: null, amount: 8000, currency: 'JPY', amountSource: 'MANUAL', memo: null, createdBy: 3 },
    { itemId: 11, scope: 'PERSONAL', category: 'ETC', name: '엽서', calcType: null, amount: 600, currency: 'JPY', amountSource: 'MANUAL', memo: null, createdBy: 3 },
    { itemId: 12, scope: 'PERSONAL', category: 'TRANSPORT', name: '교통카드 충전', calcType: null, amount: 7400, currency: 'JPY', amountSource: 'MANUAL', memo: null, createdBy: 3 },
    { itemId: 13, scope: 'PERSONAL', category: 'SIGHTSEEING', name: '공연 티켓', calcType: null, amount: 20000, currency: 'KRW', amountSource: 'MANUAL', memo: null, createdBy: 3 },
  ],
}

// ---- BUD-03 합계 (서버가 계산해서 보내 줄 결과를 미리 적어 둔 것) ----
// 위 목록 기준, 환율 100엔 = 910원 (와이어프레임 D-11 숫자와 같음)
//  공통: ¥400 × 4명 + ¥40,000 = ¥41,600 / 30,000원 × 4명 = 120,000원 / 입장권은 가격 미확인이라 뺌
//        원화 합산 41,600 × 9.1 + 120,000 = 498,560원
//  내 몫: ¥41,600 / 4 = ¥10,400, 120,000원 / 4 = 30,000원
//        + 내 개인 경비 ¥3,000, 52,000원 = ¥13,400 + 82,000원
//        원화 합산 13,400 × 9.1 + 82,000 = 203,940원

const PRICE_UNKNOWN_TICKET = { itemId: 4, name: '입장권', reason: 'PRICE_UNKNOWN' } as const

const MOCK_BUDGET_SUMMARY: BudgetSummaryResponse = {
  tripId: 1,
  commonTotal: {
    krwTotal: 498560,
    byCurrency: [
      { currency: 'JPY', amount: 41600 },
      { currency: 'KRW', amount: 120000 },
    ],
    excluded: [PRICE_UNKNOWN_TICKET],
  },
  myShare: {
    krwTotal: 203940,
    byCurrency: [
      { currency: 'JPY', amount: 13400 },
      { currency: 'KRW', amount: 82000 },
    ],
    commonShare: [
      { currency: 'JPY', amount: 10400 },
      { currency: 'KRW', amount: 30000 },
    ],
    personal: [
      { currency: 'JPY', amount: 3000 },
      { currency: 'KRW', amount: 52000 },
    ],
    excluded: [PRICE_UNKNOWN_TICKET],
  },
}

const EMPTY_SUMMARY: BudgetSummaryResponse = {
  tripId: 1,
  commonTotal: { krwTotal: 0, byCurrency: [], excluded: [] },
  myShare: { krwTotal: 0, byCurrency: [], commonShare: [], personal: [], excluded: [] },
}

// ---- BUD-04 환율 ----
// 엔화는 위 합계와 같은 100엔 = 910원 (같은 화면에서 환율이 둘이 되지 않게 맞춤)
// 달러·유로는 2026-11-25 한국수출입은행 실제 발표값

const MOCK_EXCHANGE_RATES: Record<string, ExchangeRate> = {
  JPY: { currency: 'JPY', baseDate: '2026-11-24', rate: 910, unit: 100, ratePerOne: 9.1 },
  USD: { currency: 'USD', baseDate: '2026-11-25', rate: 1343.6, unit: 1, ratePerOne: 1343.6 },
  EUR: { currency: 'EUR', baseDate: '2026-11-25', rate: 1472.72, unit: 1, ratePerOne: 1472.72 },
}

// ---- 가짜 서버 흉내 ----

function queryParam(name: string): string | null {
  if (!import.meta.env.DEV) return null
  return new URLSearchParams(window.location.search).get(name)
}

function mockMode(): string | null {
  return queryParam('mock')
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

// 서버 응답처럼 잠깐 기다렸다가 돌려줌 (loading이면 오래 기다림, error면 실패)
async function respond<T>(value: T, empty: T): Promise<T> {
  const mode = mockMode()
  await wait(mode === 'loading' ? 600_000 : 500)
  if (mode === 'error') throw new Error('mock error')
  return mode === 'empty' ? empty : value
}

export function mockGetBudgetList(tripId: number): Promise<BudgetListResponse> {
  const tripCurrency = queryParam('currency') ?? MOCK_BUDGET_LIST.tripCurrency
  const list = { ...MOCK_BUDGET_LIST, tripId, tripCurrency }
  return respond(list, { ...list, items: [] })
}

export async function mockGetExchangeRate(currency: string): Promise<ExchangeRate> {
  await wait(500)
  const rate = MOCK_EXCHANGE_RATES[currency]
  if (mockMode() === 'rate-error' || !rate) throw new Error('mock rate error')
  return rate
}

export function mockGetBudgetSummary(tripId: number): Promise<BudgetSummaryResponse> {
  return respond({ ...MOCK_BUDGET_SUMMARY, tripId }, { ...EMPTY_SUMMARY, tripId })
}
