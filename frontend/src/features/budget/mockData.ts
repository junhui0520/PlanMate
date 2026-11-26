// 경비 화면 가짜 데이터 (와이어프레임 D-11 예시). 가짜 데이터는 모두 이 파일에만 둔다.
// TODO(D): 서버 API가 생기면 budgetApi.ts가 서버를 부르게 바꾸고 이 파일은 지운다.
//
// 미리보기에서 상태 바꿔 보기: 주소 끝에
//   ?mock=loading | empty | error   (경비 화면 전체)
//   ?mock=rate-error                (환율 위젯만 실패)
//   ?mock=limit                     (경비 추가 시 200개 초과 안내)
//   ?mock=deleted                   (경비 수정·삭제 시 "이미 삭제된 경비예요.")
//   ?role=EDITOR | VIEWER           (내 역할 바꾸기, 기본 OWNER. VIEWER면 추가 버튼 없음)
//   ?currency=KRW | USD | EUR       (여행 도시 통화 바꾸기, KRW면 위젯 숨김. 기본 JPY)
// 추가한 경비는 이 파일의 목록에만 붙는다. 새로고침하면 처음으로 돌아간다.
import type {
  BudgetItem,
  BudgetItemRequest,
  BudgetListResponse,
  BudgetSummaryResponse,
  BudgetTotal,
  CurrencyAmount,
  ExcludedItem,
  ExchangeRate,
  TripRole,
} from './api/budgetApi.ts'
import { roundToCurrency } from './utils/money.ts'

// ---- BUD-01-01 경비 목록 (추가할 수 있게 let) ----

const MOCK_TRIP = {
  tripCurrency: 'JPY', // 도쿄 여행
  memberCount: 4,
  myMemberId: 1,
  participants: [
    { memberId: 1, nickname: '지은' },
    { memberId: 2, nickname: '민수' },
    { memberId: 3, nickname: '하늘' },
    { memberId: 4, nickname: '유진' },
  ],
}

// BUD-02-03 근거 칸(calcNote·estimateBasis)은 직접 입력·교통비 항목에선 비어 있음 (출처는 C 데이터, TODO)
let mockItems: BudgetItem[] = ([
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
] satisfies Omit<BudgetItem, 'calcNote' | 'estimateBasis'>[]).map((item) => ({
  ...item,
  calcNote: null,
  estimateBasis: null,
}))

// ---- BUD-04 환율 ----
// 엔화는 와이어프레임 숫자 100엔 = 910원, 달러·유로는 2026-11-25 한국수출입은행 실제 발표값

const MOCK_EXCHANGE_RATES: Record<string, ExchangeRate> = {
  JPY: { currency: 'JPY', baseDate: '2026-11-24', rate: 910, unit: 100, ratePerOne: 9.1 },
  USD: { currency: 'USD', baseDate: '2026-11-25', rate: 1343.6, unit: 1, ratePerOne: 1343.6 },
  EUR: { currency: 'EUR', baseDate: '2026-11-25', rate: 1472.72, unit: 1, ratePerOne: 1472.72 },
}

// 원화로 바꾸는 값 (원화는 1, 환율 없는 통화는 undefined)
function krwPerOne(currency: string): number | undefined {
  return currency === 'KRW' ? 1 : MOCK_EXCHANGE_RATES[currency]?.ratePerOne
}

// ---- BUD-03 합계: 서버가 할 계산을 흉내 냄 (화면은 계산하지 않음) ----

type Sums = Map<string, number>

function add(sums: Sums, currency: string, amount: number) {
  sums.set(currency, (sums.get(currency) ?? 0) + amount)
}

// 통화별 금액 목록 (통화 표준 자릿수로 마지막에 한 번 반올림, 원화는 맨 뒤)
function toAmounts(sums: Sums): CurrencyAmount[] {
  return [...sums.keys()]
    .sort((a, b) => Number(a === 'KRW') - Number(b === 'KRW'))
    .map((currency) => ({ currency, amount: roundToCurrency(sums.get(currency) ?? 0, currency) }))
}

// 원화 합산 (환율 없는 통화는 빼고 그 항목은 NO_RATE로 제외 목록에)
function toTotal(sums: Sums, items: BudgetItem[], priceUnknown: BudgetItem[]): BudgetTotal {
  const excluded: ExcludedItem[] = priceUnknown.map((i) => ({ itemId: i.itemId, name: i.name, reason: 'PRICE_UNKNOWN' }))
  let krw = 0
  for (const [currency, amount] of sums) {
    const rate = krwPerOne(currency)
    if (rate === undefined) {
      items
        .filter((i) => i.currency === currency && i.amount !== null)
        .forEach((i) => excluded.push({ itemId: i.itemId, name: i.name, reason: 'NO_RATE' }))
    } else {
      krw += amount * rate
    }
  }
  return { krwTotal: Math.round(krw), byCurrency: toAmounts(sums), excluded }
}

function computeSummary(tripId: number, items: BudgetItem[]): BudgetSummaryResponse {
  const { memberCount, myMemberId } = MOCK_TRIP
  const common = items.filter((i) => i.scope === 'COMMON')
  const mine = items.filter((i) => i.scope === 'PERSONAL' && i.createdBy === myMemberId)

  // BUD-03-01 공통 경비: 전체 금액 그대로 / 1인당 금액 x 여행 인원, 가격 확인 전은 뺌
  const commonSums: Sums = new Map()
  common.forEach((i) => {
    if (i.amount !== null) add(commonSums, i.currency, i.calcType === 'PER_PERSON' ? i.amount * memberCount : i.amount)
  })
  const commonUnknown = common.filter((i) => i.amount === null)

  // BUD-03-03 내 1인 경비 = 공통 경비 합계 / 여행 인원 + 내 개인 경비 (통화별)
  const shareSums: Sums = new Map()
  commonSums.forEach((amount, currency) => add(shareSums, currency, amount / memberCount))
  const personalSums: Sums = new Map()
  mine.forEach((i) => {
    if (i.amount !== null) add(personalSums, i.currency, i.amount)
  })
  const mySums: Sums = new Map(shareSums)
  personalSums.forEach((amount, currency) => add(mySums, currency, amount))
  const myUnknown = [...commonUnknown, ...mine.filter((i) => i.amount === null)]

  return {
    tripId,
    commonTotal: toTotal(commonSums, common, commonUnknown),
    myShare: {
      ...toTotal(mySums, [...common, ...mine], myUnknown),
      commonShare: toAmounts(shareSums),
      personal: toAmounts(personalSums),
    },
  }
}

// ---- 가짜 서버 흉내 ----

function queryParam(name: string): string | null {
  if (!import.meta.env.DEV) return null
  return new URLSearchParams(window.location.search).get(name)
}

function mockMode(): string | null {
  return queryParam('mock')
}

// 내 역할 (기본 OWNER, ?role=EDITOR | VIEWER 로 바꿔 보기)
function myRole(): TripRole {
  const role = queryParam('role')
  return role === 'EDITOR' || role === 'VIEWER' ? role : 'OWNER'
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

// 서버 응답처럼 잠깐 기다렸다가 돌려줌 (loading이면 오래 기다림, error면 실패)
async function respond<T>(value: () => T, empty: () => T): Promise<T> {
  const mode = mockMode()
  await wait(mode === 'loading' ? 600_000 : 500)
  if (mode === 'error') throw new Error('mock error')
  return mode === 'empty' ? empty() : value()
}

function currentList(tripId: number, items: BudgetItem[]): BudgetListResponse {
  const tripCurrency = queryParam('currency') ?? MOCK_TRIP.tripCurrency
  return { ...MOCK_TRIP, tripId, tripCurrency, myRole: myRole(), items: [...items] }
}

export function mockGetBudgetList(tripId: number): Promise<BudgetListResponse> {
  return respond(() => currentList(tripId, mockItems), () => currentList(tripId, []))
}

export function mockGetBudgetSummary(tripId: number): Promise<BudgetSummaryResponse> {
  return respond(() => computeSummary(tripId, mockItems), () => computeSummary(tripId, []))
}

export async function mockGetExchangeRate(currency: string): Promise<ExchangeRate> {
  await wait(500)
  const rate = MOCK_EXCHANGE_RATES[currency]
  if (mockMode() === 'rate-error' || !rate) throw new Error('mock rate error')
  return rate
}

// BUD-01-02 경비 항목 추가 (서버가 할 검사 흉내: 권한·개수)
const MAX_ITEMS = 200

export async function mockAddBudgetItem(_tripId: number, request: BudgetItemRequest): Promise<BudgetItem> {
  await wait(600)
  const { myMemberId } = MOCK_TRIP
  const limitMode = mockMode() === 'limit'
  if (request.scope === 'COMMON') {
    if (myRole() === 'VIEWER') throw new Error('공통 경비는 편집 권한이 있어야 추가할 수 있어요.')
    const count = mockItems.filter((i) => i.scope === 'COMMON').length
    if (limitMode || count >= MAX_ITEMS) throw new Error('공통 경비는 최대 200개까지 추가할 수 있어요.')
  } else {
    const count = mockItems.filter((i) => i.scope === 'PERSONAL' && i.createdBy === myMemberId).length
    if (limitMode || count >= MAX_ITEMS) throw new Error('개인 경비는 최대 200개까지 추가할 수 있어요.')
  }
  const { priceUnknown: _priceUnknown, ...values } = request
  const item: BudgetItem = {
    itemId: Math.max(0, ...mockItems.map((i) => i.itemId)) + 1,
    ...values,
    amountSource: 'MANUAL',
    calcNote: null,
    estimateBasis: null,
    createdBy: myMemberId,
  }
  mockItems = [...mockItems, item]
  return item
}

// BUD-01-04 서버가 할 검사 흉내: 없으면 "이미 삭제된 경비예요.", 권한 없으면 "이 경비를 고칠 권한이 없어요."
function findEditable(itemId: number): BudgetItem {
  const item = mockItems.find((i) => i.itemId === itemId)
  if (!item || mockMode() === 'deleted') throw new Error('이미 삭제된 경비예요.')
  const mine = item.scope === 'PERSONAL' && item.createdBy === MOCK_TRIP.myMemberId
  if (myRole() === 'VIEWER' || (item.scope === 'PERSONAL' && !mine)) {
    throw new Error('이 경비를 고칠 권한이 없어요.')
  }
  return item
}

// BUD-01-04 경비 항목 수정 (나중에 저장한 값으로 덮어씀, 금액 입력 방식·만든 사람은 그대로)
export async function mockUpdateBudgetItem(
  _tripId: number,
  itemId: number,
  request: BudgetItemRequest,
): Promise<BudgetItem> {
  await wait(600)
  const item = findEditable(itemId)
  const { priceUnknown: _priceUnknown, scope: _scope, ...values } = request
  const updated: BudgetItem = { ...item, ...values }
  mockItems = mockItems.map((i) => (i.itemId === itemId ? updated : i))
  return updated
}

// BUD-01-04 경비 항목 삭제
export async function mockDeleteBudgetItem(_tripId: number, itemId: number): Promise<void> {
  await wait(600)
  findEditable(itemId)
  mockItems = mockItems.filter((i) => i.itemId !== itemId)
}

// BUD-02-03 펼친 칸 메모 저장 (공통 경비만, 비우면 삭제 — 서버가 할 앞뒤 공백 제거 흉내)
export async function mockUpdateBudgetMemo(_tripId: number, itemId: number, memo: string | null): Promise<BudgetItem> {
  await wait(600)
  const item = findEditable(itemId)
  const trimmed = memo?.trim() ?? ''
  const updated: BudgetItem = { ...item, memo: trimmed === '' ? null : trimmed }
  mockItems = mockItems.map((i) => (i.itemId === itemId ? updated : i))
  return updated
}
