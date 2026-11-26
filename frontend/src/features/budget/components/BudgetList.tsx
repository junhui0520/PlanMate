// BUD-01-01 경비 목록: 공통 경비(분류 탭) + 개인 경비(참여자별 카드), BUD-01-04 수정·삭제 메뉴, BUD-02-03 근거·메모 펼치기
// 모양은 와이어프레임 D-11 (카드 안에 공통 경비·개인 경비)
import { useEffect, useRef, useState } from 'react'
import {
  updateBudgetMemo,
  type AmountSource,
  type BudgetCategory,
  type BudgetItem,
  type BudgetListResponse,
  type BudgetParticipant,
  type BudgetScope,
  type BudgetTotal,
  type CurrencyAmount,
} from '../api/budgetApi.ts'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { useToast } from '../../../shared/hooks/useToast'
import { formatMoney } from '../../../shared/utils/money'
import { isBlank, replaceControlChars } from '../../../shared/utils/validation'
import { formatCurrencyAmounts, roundToCurrency } from '../utils/money.ts'
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

// BUD-02-03 공통 경비 항목마다 메모 아이콘 (없음: 빈 아이콘 / 있음: 채워진 아이콘)
// 마우스를 올리면 메모 미리보기 (와이어프레임 D-11 "메모: 3박, 조식 포함"), 누르면 항목 아래로 근거·메모 칸 펼치기
function MemoIcon({ memo, expanded, onToggle }: { memo: string | null; expanded: boolean; onToggle: () => void }) {
  const hasMemo = Boolean(memo)
  return (
    <button
      type="button"
      className={hasMemo ? 'budget-square budget-memo budget-memo--filled' : 'budget-square budget-memo'}
      aria-label={hasMemo ? `메모: ${memo}` : '메모 없음'}
      aria-expanded={expanded}
      title={hasMemo ? `메모: ${memo}` : '메모 없음'}
      onClick={onToggle}
    >
      <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
        <rect x="2.5" y="1.5" width="11" height="13" rx="1.5" />
        <path d="M5 5.5h6M5 8h6M5 10.5h4" />
      </svg>
    </button>
  )
}

// BUD-02-03 펼친 칸의 금액 입력 방식 글자 (직접 입력 항목은 "직접 입력"만, 만든 사람·시각 없음)
const SOURCE_LABEL: Record<AmountSource, string> = {
  MANUAL: '직접 입력',
  BUDGET_RULE: '예산 기준',
  ROUTE_FARE: '교통비',
}

const MEMO_MAX = 200 // BUD-02-03 메모 0~200자

// BUD-02-03 펼친 칸: 금액 입력 방식·근거·메모(소유자·편집 참여자만 작성) + "실제 예약·결제 가격과 다를 수 있어요."
function BasisPanel({
  tripId,
  item,
  canEdit,
  onSaved,
}: {
  tripId: number
  item: BudgetItem
  canEdit: boolean
  onSaved: () => void
}) {
  const toast = useToast()
  const [memo, setMemo] = useState(item.memo ?? '')
  const [saving, setSaving] = useState(false)
  const changed = memo.trim() !== (item.memo ?? '')

  // [저장]: 비우고 저장하면 메모 삭제(빈 아이콘). 성공 안내는 없음(추가·수정과 같음)
  const save = async () => {
    if (saving || !changed) return
    setSaving(true)
    try {
      await updateBudgetMemo(tripId, item.itemId, isBlank(memo) ? null : memo.trim())
      onSaved()
    } catch (e) {
      toast.show({ message: e instanceof Error ? e.message : '', type: 'error' })
      setSaving(false)
    }
  }

  return (
    <div className="budget-basis">
      <dl className="budget-basis__rows">
        <dt>금액 입력 방식</dt>
        <dd>{SOURCE_LABEL[item.amountSource]}</dd>
        {/* TODO(C에 요청): 교통비 항목의 출처·확인 시점("출처: 카카오맵 · 10.28 기준")은 FareService에서 받는다 */}
        {item.calcNote && (
          <>
            <dt>금액 계산 설명</dt>
            <dd>{item.calcNote}</dd>
          </>
        )}
        {item.estimateBasis && (
          <>
            <dt>자동 계산 기준값</dt>
            <dd>{item.estimateBasis}</dd>
          </>
        )}
        {!canEdit && item.memo && (
          <>
            <dt>메모</dt>
            <dd>{item.memo}</dd>
          </>
        )}
      </dl>
      {/* 메모 작성·수정·삭제는 소유자·편집 참여자만, 조회 참여자는 보기만 */}
      {canEdit && (
        <div className="budget-basis__memo">
          <Input
            label="메모"
            value={memo}
            maxChars={MEMO_MAX}
            showCount
            onValueChange={(v) => setMemo(replaceControlChars(v))}
          />
          <Button variant="secondary" disabled={!changed} loading={saving} loadingText="저장 중..." onClick={save}>
            저장
          </Button>
        </div>
      )}
      <p className="budget-basis__note">실제 예약·결제 가격과 다를 수 있어요.</p>
    </div>
  )
}

// BUD-01-04 공통 경비 항목 메뉴(점 3개) → [수정] [삭제]. 공통 부품에 없는 작은 메뉴라 경비 안에 둠
function ItemMenu({ onEdit, onDelete }: { onEdit: () => void; onDelete: () => void }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLSpanElement>(null)

  // 메뉴 밖을 누르면 닫힘
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  const pick = (action: () => void) => {
    setOpen(false)
    action()
  }

  return (
    <span className="budget-menu" ref={ref}>
      <button
        type="button"
        className="budget-square"
        aria-label="항목 메뉴"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        ···
      </button>
      {open && (
        <span className="budget-menu__list" role="menu">
          <button type="button" role="menuitem" onClick={() => pick(onEdit)}>
            수정
          </button>
          <button type="button" role="menuitem" onClick={() => pick(onDelete)}>
            삭제
          </button>
        </span>
      )}
    </span>
  )
}

function CommonSection({
  tripId,
  items,
  memberCount,
  canEdit,
  commonTotal,
  onAdd,
  onEdit,
  onDelete,
  onChanged,
}: {
  tripId: number
  items: BudgetItem[]
  memberCount: number
  canEdit: boolean
  commonTotal: BudgetTotal
  onAdd: () => void
  onEdit: (item: BudgetItem) => void
  onDelete: (item: BudgetItem) => void
  onChanged: () => void
}) {
  const [tab, setTab] = useState<BudgetCategory | 'ALL'>('ALL')
  const shown = tab === 'ALL' ? items : items.filter((item) => item.category === tab)
  // BUD-02-03 펼친 항목 (메모 아이콘을 누를 때마다 열고 닫음, 여러 개를 같이 펼칠 수 있음)
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const toggle = (itemId: number) =>
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(itemId)) next.delete(itemId)
      else next.add(itemId)
      return next
    })

  return (
    <section className="budget-box">
      <div className="budget-box__head">
        <h2 className="budget-section__title">
          공통 경비 <span className="budget-count">· {items.length}건</span>
        </h2>
        {/* BUD-01-02 공통 경비 추가 (소유자·편집 참여자만) */}
        {canEdit && (
          <Button variant="secondary" onClick={onAdd}>
            공통 경비 추가
          </Button>
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
              <MemoIcon
                memo={item.memo}
                expanded={expanded.has(item.itemId)}
                onToggle={() => toggle(item.itemId)}
              />
              {/* BUD-01-04 공통 경비: 항목 메뉴 → [수정] / [삭제] (소유자·편집 참여자만) */}
              {canEdit && <ItemMenu onEdit={() => onEdit(item)} onDelete={() => onDelete(item)} />}
              {/* BUD-02-03 항목 아래로 펼친 칸 (메모가 바뀌면 칸을 새 값으로 다시 채움) */}
              {expanded.has(item.itemId) && (
                <BasisPanel
                  key={`${item.itemId}-${item.memo ?? ''}`}
                  tripId={tripId}
                  item={item}
                  canEdit={canEdit}
                  onSaved={onChanged}
                />
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
  onEdit,
  onDelete,
}: {
  participant: BudgetParticipant
  items: BudgetItem[]
  isMine: boolean
  canEdit: boolean
  color: string
  onAdd: () => void
  onEdit: (item: BudgetItem) => void
  onDelete: (item: BudgetItem) => void
}) {
  const editable = isMine && canEdit // BUD-01-04 내 개인 경비만 (다른 사람 카드에는 없음)
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
        {shown.map((item) => {
          const amountText =
            item.amount === null ? (
              <span className="budget-unknown">가격 확인 전</span>
            ) : (
              formatMoney(item.amount, item.currency)
            )
          // 사용자 요청으로 추가(기능정의서·피그마에 없음): 이름 옆 작은 회색 분류.
          // 이름이 길면 이름만 …로 줄고 분류는 잘리지 않음
          const nameAndAmount = (
            <>
              <span className="budget-card__item-name">
                <span className="budget-card__item-text" title={item.name}>
                  {item.name}
                </span>
                <span className="budget-card__item-category">{CATEGORY_LABEL[item.category]}</span>
              </span>
              <span className="budget-card__amount">{amountText}</span>
            </>
          )
          return (
            <li key={item.itemId} className="budget-card__item">
              {editable ? (
                // BUD-01-04 내 개인 경비: 항목을 누르면 수정 창
                <button
                  type="button"
                  className="budget-card__item-button"
                  aria-label={`${item.name} 수정`}
                  onClick={() => onEdit(item)}
                >
                  {nameAndAmount}
                </button>
              ) : (
                nameAndAmount
              )}
              {/* BUD-01-04 내 개인 경비: 항목 오른쪽 x는 삭제 */}
              {editable && (
                <button
                  type="button"
                  className="budget-icon-button"
                  aria-label={`${item.name} 삭제`}
                  onClick={() => onDelete(item)}
                >
                  ×
                </button>
              )}
            </li>
          )
        })}
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
  onEdit,
  onDelete,
}: {
  data: BudgetListResponse
  myPersonal: CurrencyAmount[]
  onAdd: () => void
  onEdit: (item: BudgetItem) => void
  onDelete: (item: BudgetItem) => void
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
              onEdit={onEdit}
              onDelete={onDelete}
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
  onEdit,
  onDelete,
  onChanged,
}: {
  data: BudgetListResponse
  commonTotal: BudgetTotal
  myPersonal: CurrencyAmount[]
  onAdd: (scope: BudgetScope) => void // BUD-01-02 추가 창 열기
  onEdit: (item: BudgetItem) => void // BUD-01-04 수정 창 열기
  onDelete: (item: BudgetItem) => void // BUD-01-04 삭제 확인 창 열기
  onChanged: () => void // BUD-02-03 메모 저장 후 목록 다시 불러오기
}) {
  const commonItems = data.items.filter((item) => item.scope === 'COMMON')
  const canEditCommon = data.myRole === 'OWNER' || data.myRole === 'EDITOR'

  return (
    <>
      <CommonSection
        tripId={data.tripId}
        onChanged={onChanged}
        items={commonItems}
        memberCount={data.memberCount}
        canEdit={canEditCommon}
        commonTotal={commonTotal}
        onAdd={() => onAdd('COMMON')}
        onEdit={onEdit}
        onDelete={onDelete}
      />
      <PersonalSection
        data={data}
        myPersonal={myPersonal}
        onAdd={() => onAdd('PERSONAL')}
        onEdit={onEdit}
        onDelete={onDelete}
      />
    </>
  )
}
