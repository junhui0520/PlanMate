// BUD-01-01 경비 목록: 공통 경비(분류 탭) + 개인 경비(참여자별 카드)
// 모양은 와이어프레임 D-11 (카드 안에 공통 경비·개인 경비)
import { useState } from 'react'
import type {
  AmountSource,
  BudgetCategory,
  BudgetItem,
  BudgetListResponse,
  BudgetParticipant,
  BudgetScope,
  BudgetTotal,
  CurrencyAmount,
} from '../api/budgetApi.ts'
import { formatCurrencyAmounts, formatMoney, roundToCurrency } from '../utils/money.ts'
import { CommonTotal } from './BudgetSummary.tsx'

// BUD-01-01 개인 경비 카드 머리의 통화별 합계. 예: ¥3,000 + 52,000원 (원화는 맨 뒤)
function formatCurrencyTotals(items: BudgetItem[]): string {
  const totals = new Map<string, number>()
  for (const item of items) {
    if (item.amount === null) continue // 가격 확인 전은 합계에서 뺌
    totals.set(item.currency, (totals.get(item.currency) ?? 0) + item.amount)
  }
  const currencies = [...totals.keys()].sort((a, b) => Number(a === 'KRW') - Number(b === 'KRW'))
  return currencies
    .map((c) => formatMoney(roundToCurrency(totals.get(c) ?? 0, c), c))
    .join(' + ')
}

// ---- 공통 경비 ----

const CATEGORY_TABS: { key: BudgetCategory | 'ALL'; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'TRANSPORT', label: '교통' },
  { key: 'LODGING', label: '숙박' },
  { key: 'FOOD', label: '식비' },
  { key: 'SIGHTSEEING', label: '관광' },
  { key: 'ETC', label: '기타' },
]

// 분류 이름 (개인 경비 카드 항목 옆 표시용)
const CATEGORY_LABEL = Object.fromEntries(
  CATEGORY_TABS.filter((t) => t.key !== 'ALL').map((t) => [t.key, t.label]),
) as Record<BudgetCategory, string>

// 와이어프레임 D-11 배지: 자동으로 들어온 금액이면 출처 표시 (직접 입력은 표시 없음)
const SOURCE_BADGE: Partial<Record<AmountSource, string>> = {
  BUDGET_RULE: '예산 기준',
  ROUTE_FARE: '교통비',
}

// BUD-01-01 가격 확인 전은 "가격 확인 전", 무료는 "0원"으로 다르게 표시
function CommonAmount({ item, memberCount }: { item: BudgetItem; memberCount: number }) {
  if (item.amount === null) return <span className="budget-unknown">가격 확인 전</span>
  if (item.calcType === 'PER_PERSON') {
    const total = roundToCurrency(item.amount * memberCount, item.currency)
    return (
      <span>
        {formatMoney(item.amount, item.currency)} × {memberCount}명 ={' '}
        {formatMoney(total, item.currency)}
      </span>
    )
  }
  return <span>{formatMoney(item.amount, item.currency)}</span>
}

// BUD-01-01 공통 경비 항목마다 메모 아이콘 (없음: 빈 아이콘 / 있음: 채워진 아이콘)
// 마우스를 올리면 메모 미리보기 (와이어프레임 D-11 "메모: 3박, 조식 포함")
// TODO(D): BUD-02-03 누르면 아래로 계산 근거·메모 펼치기
function MemoIcon({ memo }: { memo: string | null }) {
  const hasMemo = Boolean(memo)
  return (
    <span
      className={hasMemo ? 'budget-square budget-memo budget-memo--filled' : 'budget-square budget-memo'}
      role="img"
      aria-label={hasMemo ? `메모: ${memo}` : '메모 없음'}
      title={hasMemo ? `메모: ${memo}` : '메모 없음'}
    >
      <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
        <rect x="2.5" y="1.5" width="11" height="13" rx="1.5" />
        <path d="M5 5.5h6M5 8h6M5 10.5h4" />
      </svg>
    </span>
  )
}

function CommonSection({
  items,
  memberCount,
  canEdit,
  commonTotal,
  onAdd,
}: {
  items: BudgetItem[]
  memberCount: number
  canEdit: boolean
  commonTotal: BudgetTotal
  onAdd: () => void
}) {
  const [tab, setTab] = useState<BudgetCategory | 'ALL'>('ALL')
  const shown = tab === 'ALL' ? items : items.filter((item) => item.category === tab)

  return (
    <section className="budget-box">
      <div className="budget-box__head">
        <h2 className="budget-section__title">
          공통 경비 <span className="budget-count">· {items.length}건</span>
        </h2>
        {/* BUD-01-02 공통 경비 추가 (소유자·편집 참여자만) */}
        {canEdit && (
          <button type="button" className="budget-outline-button" onClick={onAdd}>
            공통 경비 추가
          </button>
        )}
      </div>

      <div className="budget-tabs" role="tablist">
        {CATEGORY_TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            className={tab === t.key ? 'budget-tab budget-tab--active' : 'budget-tab'}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <ul className="budget-common-list">
        {shown.map((item) => {
          const badge = SOURCE_BADGE[item.amountSource]
          return (
            <li key={item.itemId} className="budget-common-item">
              <span className="budget-common-item__name">
                {item.name}
                {/* 계산 방식(가격 확인 전이면 생략) · 자동 입력 배지 */}
                <span className="budget-common-item__calc">
                  {[
                    item.amount === null ? null : item.calcType === 'PER_PERSON' ? '1인당 금액' : '전체 금액',
                    badge,
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </span>
              </span>
              <span className="budget-common-item__amount">
                <CommonAmount item={item} memberCount={memberCount} />
              </span>
              <MemoIcon memo={item.memo} />
              {/* TODO(D): BUD-01-04 [수정]·[삭제] 메뉴 */}
              {canEdit && (
                <button type="button" className="budget-square" aria-label="항목 메뉴">
                  ···
                </button>
              )}
            </li>
          )
        })}
      </ul>
      {/* BUD-03-01 공통 경비 합계는 [전체] 탭 맨 오른쪽 아래에만 */}
      {tab === 'ALL' && <CommonTotal total={commonTotal} />}
    </section>
  )
}

// ---- 개인 경비 ----

const PREVIEW_COUNT = 3 // BUD-01-01 3건이 넘으면 "n건 더 보기"
const CARD_COLORS = ['blue', 'purple', 'green', 'orange', 'pink'] // 참여자 카드 배경색 (내 카드가 첫 색)

function PersonalCard({
  participant,
  items,
  isMine,
  canEdit,
  color,
  onAdd,
}: {
  participant: BudgetParticipant
  items: BudgetItem[]
  isMine: boolean
  canEdit: boolean
  color: string
  onAdd: () => void
}) {
  const [expanded, setExpanded] = useState(false)
  const shown = expanded ? items : items.slice(0, PREVIEW_COUNT)
  const hiddenCount = items.length - shown.length
  const totals = formatCurrencyTotals(items)

  return (
    <article className={`budget-card budget-card--${color}${isMine ? ' budget-card--mine' : ''}`}>
      <header className="budget-card__head">
        <span className="budget-card__name">
          {participant.nickname}
          {isMine && ' (나)'}
        </span>
        {/* BUD-01-02 내 개인 경비 추가 (내 카드만) */}
        {isMine && canEdit && (
          <button type="button" className="budget-square" aria-label="개인 경비 추가" onClick={onAdd}>
            +
          </button>
        )}
      </header>
      {totals && <div className="budget-card__total">{totals}</div>}
      <ul className="budget-card__list">
        {shown.map((item) => (
          <li key={item.itemId} className="budget-card__item">
            {/* 사용자 요청으로 추가(기능정의서·피그마에 없음): 이름 옆 작은 회색 분류.
                이름이 길면 이름만 …로 줄고 분류는 잘리지 않음 */}
            <span className="budget-card__item-name">
              <span className="budget-card__item-text" title={item.name}>
                {item.name}
              </span>
              <span className="budget-card__item-category">{CATEGORY_LABEL[item.category]}</span>
            </span>
            <span className="budget-card__amount">
              {item.amount === null ? (
                <span className="budget-unknown">가격 확인 전</span>
              ) : (
                formatMoney(item.amount, item.currency)
              )}
              {/* TODO(D): BUD-01-04 내 개인 경비 삭제 */}
              {isMine && canEdit && (
                <button type="button" className="budget-icon-button" aria-label="삭제">
                  ×
                </button>
              )}
            </span>
          </li>
        ))}
      </ul>
      {hiddenCount > 0 && (
        <button type="button" className="budget-more" onClick={() => setExpanded(true)}>
          {hiddenCount}건 더 보기
        </button>
      )}
    </article>
  )
}

function PersonalSection({
  data,
  myPersonal,
  onAdd,
}: {
  data: BudgetListResponse
  myPersonal: CurrencyAmount[]
  onAdd: () => void
}) {
  const personalItems = data.items.filter((item) => item.scope === 'PERSONAL')
  const itemsOf = (memberId: number) => personalItems.filter((item) => item.createdBy === memberId)
  const canEdit = data.myRole !== 'VIEWER' // 조회 참여자는 개인 경비도 입력 불가

  // 내 카드가 맨 앞, 다른 참여자는 개인 경비가 있을 때만 카드 표시
  const me = data.participants.find((p) => p.memberId === data.myMemberId)
  const others = data.participants.filter(
    (p) => p.memberId !== data.myMemberId && itemsOf(p.memberId).length > 0,
  )
  const cards = [...(me ? [me] : []), ...others]
  const myTotal = formatCurrencyAmounts(myPersonal) // 서버가 계산한 내 개인 경비 합계(BUD-03-03)

  return (
    <section className="budget-box">
      <h2 className="budget-section__title">
        개인 경비{' '}
        <span className="budget-count">
          · {personalItems.length}건{myTotal && ` · 내 개인 경비 ${myTotal}`}
        </span>
      </h2>
      <div className="budget-cards">
        {cards.map((p, index) => {
          const isMine = p.memberId === data.myMemberId
          return (
            <PersonalCard
              key={p.memberId}
              participant={p}
              items={itemsOf(p.memberId)}
              isMine={isMine}
              canEdit={isMine && canEdit}
              color={CARD_COLORS[index % CARD_COLORS.length]}
              onAdd={onAdd}
            />
          )
        })}
      </div>
    </section>
  )
}

// ---- 목록 ----

export default function BudgetList({
  data,
  commonTotal,
  myPersonal,
  onAdd,
}: {
  data: BudgetListResponse
  commonTotal: BudgetTotal
  myPersonal: CurrencyAmount[]
  onAdd: (scope: BudgetScope) => void // BUD-01-02 추가 창 열기
}) {
  const commonItems = data.items.filter((item) => item.scope === 'COMMON')
  const canEditCommon = data.myRole === 'OWNER' || data.myRole === 'EDITOR'

  return (
    <>
      <CommonSection
        items={commonItems}
        memberCount={data.memberCount}
        canEdit={canEditCommon}
        commonTotal={commonTotal}
        onAdd={() => onAdd('COMMON')}
      />
      <PersonalSection data={data} myPersonal={myPersonal} onAdd={() => onAdd('PERSONAL')} />
    </>
  )
}

// BUD-01-01 불러오는 중: 목록 자리에 빈 카드 3개(스켈레톤), 스피너 없음
// TODO(공통): shared/components/Loading.tsx가 생기면 그쪽으로 옮긴다.
export function BudgetListSkeleton() {
  return (
    <div className="budget-skeleton" aria-label="불러오는 중">
      {[0, 1, 2].map((i) => (
        <div key={i} className="budget-skeleton__card" />
      ))}
    </div>
  )
}
