// BUD-03 경비 합계 표시: 내 1인 경비(맨 위 "경비 합계" 카드), 공통 경비 합계([전체] 탭 아래), 부분 합계 안내
// 계산은 서버에서만 한다(BUD-03-01). 이 파일은 받은 값을 보여주기만 한다.
import { useState } from 'react'
import type { BudgetTotal, ExcludeReason, MyShareTotal } from '../api/budgetApi.ts'
import { formatCurrencyAmounts, formatKrwTotal } from '../utils/money.ts'

// BUD-03-04 제외 사유 글자
const EXCLUDE_REASON_LABEL: Record<ExcludeReason, string> = {
  PRICE_UNKNOWN: '가격 미확인',
  NO_RATE: '환율 없음',
}

/**
 * BUD-03-04 "부분 합계 · n건 제외"(밑줄 글자), 누르면 제외 항목과 사유 목록.
 * lead가 있으면 같은 줄 앞에 붙임 (예: "= ¥41,600 + 120,000원 · 부분 합계 · 1건 제외")
 */
function PartialNotice({
  total,
  lead,
  className,
}: {
  total: BudgetTotal
  lead?: string
  className?: string
}) {
  const [open, setOpen] = useState(false)
  const hasExcluded = total.excluded.length > 0
  if (!lead && !hasExcluded) return null

  return (
    <>
      <div className={className}>
        {lead}
        {lead && hasExcluded && ' · '}
        {hasExcluded && (
          <button
            type="button"
            className="budget-partial__toggle"
            aria-expanded={open}
            onClick={() => setOpen((v) => !v)}
          >
            부분 합계 · {total.excluded.length}건 제외
          </button>
        )}
      </div>
      {open && (
        <ul className="budget-partial__list">
          {total.excluded.map((item) => (
            <li key={`${item.itemId}-${item.reason}`}>
              {item.name} · {EXCLUDE_REASON_LABEL[item.reason]}
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

// BUD-03-03 경비 탭 맨 위 "경비 합계" 카드: 내 1인 경비만 (다른 참여자 것은 보여주지 않음, 적용 환율 표시 없음)
export function MyShareSummary({ myShare }: { myShare: MyShareTotal }) {
  const commonShare = formatCurrencyAmounts(myShare.commonShare)
  const personal = formatCurrencyAmounts(myShare.personal)
  const breakdown = [
    commonShare && `공통 경비 몫 ${commonShare}`,
    personal && `개인 경비 ${personal}`,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <section className="budget-box">
      <h2 className="budget-section__title">경비 합계</h2>
      <div className="budget-myshare" aria-label="내 1인 경비">
        <div className="budget-myshare__label">내 1인 경비</div>
        <div className="budget-myshare__krw">{formatKrwTotal(myShare.krwTotal)}</div>
        {myShare.byCurrency.length > 0 && (
          <div className="budget-myshare__currency">= {formatCurrencyAmounts(myShare.byCurrency)}</div>
        )}
        {breakdown && <div className="budget-myshare__breakdown">{breakdown}</div>}
        <PartialNotice total={myShare} className="budget-myshare__partial" />
      </div>
    </section>
  )
}

// BUD-03-01 공통 경비 [전체] 탭 맨 오른쪽 아래: 원화 합산 + 아래 줄 통화별 합계(·부분 합계)
export function CommonTotal({ total }: { total: BudgetTotal }) {
  const currencyLine =
    total.byCurrency.length > 0 ? `= ${formatCurrencyAmounts(total.byCurrency)}` : undefined

  return (
    <div className="budget-common-total">
      <div className="budget-common-total__krw">공통 경비 합계 {formatKrwTotal(total.krwTotal)}</div>
      <PartialNotice total={total} lead={currencyLine} className="budget-common-total__currency" />
    </div>
  )
}
