// 경비 서버 호출 (BUD-01-01 목록, BUD-01-02 추가, BUD-01-04 수정·삭제, BUD-02-03 메모, BUD-03 합계, BUD-04 환율)
// 지금은 가짜 데이터(mockData.ts)를 돌려준다. 화면 파일은 이 파일의 함수만 부른다.
// TODO(D): contracts/budget.yaml과 서버 API가 생기면
//          1) 아래 타입을 yaml에 맞추고
//          2) 각 함수 안쪽을 shared/api/client.ts 호출로 바꾸고
//          3) mockData.ts를 지운다. (화면 파일은 고치지 않아도 됨)
import {
  mockAddBudgetItem,
  mockDeleteBudgetItem,
  mockGetBudgetList,
  mockGetBudgetSummary,
  mockGetExchangeRate,
  mockUpdateBudgetItem,
  mockUpdateBudgetMemo,
} from '../mockData.ts'

// 경비 구분 (BUDGET_ITEM.SCOPE)
export type BudgetScope = 'COMMON' | 'PERSONAL'
// 분류 (BUDGET_ITEM.CATEGORY)
export type BudgetCategory = 'TRANSPORT' | 'LODGING' | 'FOOD' | 'SIGHTSEEING' | 'ETC'
// 계산 방식, 공통 경비만 (BUDGET_ITEM.CALC_TYPE)
export type CalcType = 'TOTAL' | 'PER_PERSON'
// 금액 입력 방식 (BUDGET_ITEM.AMOUNT_SOURCE)
export type AmountSource = 'MANUAL' | 'BUDGET_RULE' | 'ROUTE_FARE'
// 여행 역할 (TRIP_MEMBER.ROLE)
export type TripRole = 'OWNER' | 'EDITOR' | 'VIEWER'

// ---- BUD-01-01 경비 목록 ----

export interface BudgetItem {
  itemId: number
  scope: BudgetScope
  category: BudgetCategory
  name: string
  calcType: CalcType | null // 개인 경비는 비움
  amount: number | null // 가격 확인 전이면 비움(0원과 다름)
  currency: string // 통화 코드 3자리 (JPY, KRW ...)
  amountSource: AmountSource
  calcNote: string | null // BUD-02-03 금액 계산 설명
  estimateBasis: string | null // BUD-02-03 자동 계산 기준값 (예산 기준 계산 항목)
  memo: string | null // 공통 경비만
  createdBy: number | null // 개인 경비 주인
}

// BUD-01-02 추가·BUD-01-04 수정할 때 보내는 값 (contracts/budget.yaml BudgetItemRequest, 만든 사람은 서버가 채움)
export interface BudgetItemRequest {
  scope: BudgetScope // 누른 버튼으로 정해짐 (수정에서는 원래 값 그대로)
  category: BudgetCategory
  name: string // 1~50자, 앞뒤 공백 제거
  calcType: CalcType | null // 공통만 필수, 개인은 null
  amount: number | null // 가격 확인 전이면 null
  priceUnknown: boolean // '가격 확인 전' 선택 (금액 빈칸과 구분)
  currency: string
  memo: string | null // 공통만, 0~200자
  budgetRule: BudgetRule | null // BUD-02-01 예산 기준 계산으로 넣을 때만 (금액은 서버가 계산, amount는 null)
}

// BUD-02-01 예산 기준: 기준 금액 x 선택한 값들 (곱할 값이 없으면 기준 금액 그대로)
export interface BudgetRule {
  baseAmount: number | null
  multiplyPeople: boolean // x 여행 인원 (자동)
  days: number | null // x 일수 1~7 (안 곱하면 null)
  times: number | null // x 횟수 1~99 (안 곱하면 null)
}

export interface BudgetParticipant {
  memberId: number
  nickname: string
}

export interface BudgetListResponse {
  tripId: number
  tripCurrency: string // 여행 도시 통화 (B의 여행 정보, KRW면 국내 여행)
  memberCount: number // 여행 인원 (1인당 금액 표시에 씀)
  tripDays: number // 여행 일수 (B의 여행 정보, BUD-02-01 일수 기본값)
  myMemberId: number
  myRole: TripRole
  participants: BudgetParticipant[]
  items: BudgetItem[] // 추가한 순서
}

// ---- BUD-03 합계 (계산은 서버에서만, 화면은 받은 값을 보여주기만 함) ----

// 통화 하나의 합계 (통화 표준 자릿수로 반올림된 값)
export interface CurrencyAmount {
  currency: string
  amount: number
}

// BUD-03-04 제외 사유: 가격 미확인(통화별·원화 모두에서 뺌) / 환율 없음(원화 합산에서만 뺌)
export type ExcludeReason = 'PRICE_UNKNOWN' | 'NO_RATE'

export interface ExcludedItem {
  itemId: number
  name: string
  reason: ExcludeReason
}

// 합계 한 덩어리
export interface BudgetTotal {
  krwTotal: number // BUD-04-02 원화 합산 (원 단위 반올림)
  byCurrency: CurrencyAmount[] // 통화별 합계
  excluded: ExcludedItem[] // BUD-03-04 계산에서 빠진 항목 (없으면 빈 목록)
}

// BUD-03-03 내 1인 경비 = 공통 경비 몫 + 내 개인 경비
export interface MyShareTotal extends BudgetTotal {
  commonShare: CurrencyAmount[] // 공통 경비 합계 / 여행 인원
  personal: CurrencyAmount[] // 내 개인 경비 합계
}

export interface BudgetSummaryResponse {
  tripId: number
  commonTotal: BudgetTotal // BUD-03-01 공통 경비 합계
  myShare: MyShareTotal // BUD-03-03 내 1인 경비
}

// ---- BUD-04 환율 (서버 dto/ExchangeRateResponse와 같은 모양) ----

export interface ExchangeRate {
  currency: string // 통화 코드
  baseDate: string // 발표 날짜 (yyyy-MM-dd)
  rate: number // 단위당 원화 (예: 100엔 = 910원이면 910)
  unit: number // 1 또는 100
  ratePerOne: number // 1단위당 원화 (예: 9.1)
}

// ---- 서버 호출 함수 ----

// BUD-01-01 여행 경비 목록 (공통·개인 모두)
export function getBudgetList(tripId: number): Promise<BudgetListResponse> {
  return mockGetBudgetList(tripId)
}

// BUD-03-01·03·04 경비 합계 (공통 경비 합계, 내 1인 경비, 부분 합계)
export function getBudgetSummary(tripId: number): Promise<BudgetSummaryResponse> {
  return mockGetBudgetSummary(tripId)
}

// BUD-01-02 경비 항목 추가. 실패하면 Error.message에 서버 안내 문구가 들어 있음
export function addBudgetItem(tripId: number, request: BudgetItemRequest): Promise<BudgetItem> {
  return mockAddBudgetItem(tripId, request)
}

// BUD-01-04 경비 항목 수정. 실패하면 Error.message에 서버 안내 문구 ("이미 삭제된 경비예요." 등)
export function updateBudgetItem(tripId: number, itemId: number, request: BudgetItemRequest): Promise<BudgetItem> {
  return mockUpdateBudgetItem(tripId, itemId, request)
}

// BUD-01-04 경비 항목 삭제
export function deleteBudgetItem(tripId: number, itemId: number): Promise<void> {
  return mockDeleteBudgetItem(tripId, itemId)
}

// BUD-02-03 펼친 칸 메모 저장 (공통 경비만, null이면 삭제)
export function updateBudgetMemo(tripId: number, itemId: number, memo: string | null): Promise<BudgetItem> {
  return mockUpdateBudgetMemo(tripId, itemId, memo)
}

// BUD-04-03 환율 위젯: 여행 도시 통화의 오늘 환율 (모든 여행이 같은 오늘 환율을 씀)
export function getExchangeRate(currency: string): Promise<ExchangeRate> {
  return mockGetExchangeRate(currency)
}
