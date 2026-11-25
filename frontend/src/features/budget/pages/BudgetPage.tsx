// BUD-01-01·BUD-03·BUD-04-03 예상 경비 화면 (/trips/:id/budget)
// 배치(와이어프레임 D-11): 왼쪽 = 내 1인 경비·공통 경비·개인 경비, 오른쪽 = 환율 위젯
import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import {
  getBudgetList,
  getBudgetSummary,
  type BudgetListResponse,
  type BudgetSummaryResponse,
} from '../api/budgetApi.ts'
import BudgetList, { BudgetListSkeleton } from '../components/BudgetList.tsx'
import { MyShareSummary } from '../components/BudgetSummary.tsx'
import ExchangeRateWidget from '../components/ExchangeRateWidget.tsx'
import './BudgetPage.css'

type LoadState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'done'; data: BudgetListResponse; summary: BudgetSummaryResponse }

export default function BudgetPage() {
  const { id } = useParams()
  const tripId = Number(id)
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let ignore = false // 화면을 떠났거나 다시 불러오면 이전 응답은 버림
    // 목록과 합계를 같이 불러옴 (둘 중 하나라도 실패하면 실패 안내)
    Promise.all([getBudgetList(tripId), getBudgetSummary(tripId)])
      .then(([data, summary]) => {
        if (!ignore) setState({ status: 'done', data, summary })
      })
      .catch(() => {
        if (!ignore) setState({ status: 'error' })
      })
    return () => {
      ignore = true
    }
  }, [tripId, reloadKey])

  // [다시 시도]
  const retry = () => {
    setState({ status: 'loading' })
    setReloadKey((k) => k + 1)
  }

  // BUD-04-03 환율 위젯: 경비 탭에만, 국내 여행(원화)은 표시하지 않음
  const widgetCurrency =
    state.status === 'done' && state.data.tripCurrency !== 'KRW' ? state.data.tripCurrency : null

  return (
    <main className={widgetCurrency ? 'budget-page budget-page--with-aside' : 'budget-page'}>
      <div className="budget-main">
        {state.status === 'loading' && <BudgetListSkeleton />}

        {/* BUD-01-01 실패 안내 */}
        {state.status === 'error' && (
          <div className="budget-message">
            <p>경비를 불러오지 못했어요.</p>
            {/* TODO(공통): shared/components/Button.tsx가 생기면 바꾼다. */}
            <button type="button" className="budget-retry" onClick={retry}>
              다시 시도
            </button>
          </div>
        )}

        {/* BUD-01-01 항목 없음 안내 */}
        {/* TODO(공통): shared/components/EmptyState.tsx가 생기면 바꾼다. */}
        {state.status === 'done' && state.data.items.length === 0 && (
          <div className="budget-message">
            <p>아직 경비가 없어요. 항목을 추가해 보세요.</p>
          </div>
        )}

        {state.status === 'done' && state.data.items.length > 0 && (
          <>
            {/* BUD-03-03 경비 탭 맨 위에는 내 1인 경비만 */}
            <MyShareSummary myShare={state.summary.myShare} />
            <BudgetList
              data={state.data}
              commonTotal={state.summary.commonTotal}
              myPersonal={state.summary.myShare.personal}
            />
          </>
        )}
      </div>

      {widgetCurrency && (
        <aside className="budget-aside">
          <ExchangeRateWidget currency={widgetCurrency} />
        </aside>
      )}
    </main>
  )
}
