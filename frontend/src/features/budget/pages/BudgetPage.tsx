// BUD-01-01·02·04, BUD-03, BUD-04-03 예상 경비 화면 (/trips/:tripId/budget)
// 배치(와이어프레임 D-11): 왼쪽 = 경비 합계·공통 경비·개인 경비, 오른쪽 = 환율 위젯
import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { Button } from '../../../shared/components/Button'
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Loading } from '../../../shared/components/Loading'
import { useToast } from '../../../shared/hooks/useToast'
import {
  deleteBudgetItem,
  getBudgetList,
  getBudgetSummary,
  type BudgetItem,
  type BudgetListResponse,
  type BudgetScope,
  type BudgetSummaryResponse,
} from '../api/budgetApi.ts'
import BudgetItemForm from '../components/BudgetItemForm.tsx'
import BudgetList from '../components/BudgetList.tsx'
import { MyShareSummary } from '../components/BudgetSummary.tsx'
import ExchangeRateWidget from '../components/ExchangeRateWidget.tsx'
import './BudgetPage.css'

type LoadState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'done'; data: BudgetListResponse; summary: BudgetSummaryResponse }

function loadAll(tripId: number) {
  // 목록과 합계를 같이 불러옴 (둘 중 하나라도 실패하면 실패 안내)
  return Promise.all([getBudgetList(tripId), getBudgetSummary(tripId)])
}

export default function BudgetPage() {
  const { tripId: tripIdParam } = useParams()
  const tripId = Number(tripIdParam)
  const toast = useToast() // 화면 아래 가운데 상태 창
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [reloadKey, setReloadKey] = useState(0)
  // 열린 추가·수정 창 (BUD-01-02 추가는 item 없음, BUD-01-04 수정은 item 있음)
  const [form, setForm] = useState<{ scope: BudgetScope; item?: BudgetItem } | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<BudgetItem | null>(null) // BUD-01-04 삭제 확인 창
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let ignore = false // 화면을 떠났거나 다시 불러오면 이전 응답은 버림
    loadAll(tripId)
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

  // 목록·합계를 다시 불러옴 (빈 카드 없이 바로 바뀜, 별도 안내 없음) — BUD-03 합계 다시 계산
  const refresh = () => {
    loadAll(tripId)
      .then(([data, summary]) => setState({ status: 'done', data, summary }))
      .catch(() => setState({ status: 'error' }))
  }

  // BUD-01-02·04 저장 성공: 창을 닫고 다시 불러옴
  const onSaved = () => {
    setForm(null)
    refresh()
  }

  // BUD-01-04 삭제 확인 창 [삭제]: 처리 중이면 "삭제 중..."
  const confirmDelete = async () => {
    if (!deleteTarget || deleting) return
    setDeleting(true)
    try {
      await deleteBudgetItem(tripId, deleteTarget.itemId)
      refresh() // 목록에서 제거, 합계 다시 계산
    } catch (e) {
      // "이미 삭제된 경비예요." / "이 경비를 고칠 권한이 없어요." (서버 문구 그대로)
      toast.show({ message: e instanceof Error ? e.message : '', type: 'error' })
      refresh() // 이미 삭제됐으면 목록에서도 사라지게
    } finally {
      setDeleting(false)
      setDeleteTarget(null)
    }
  }

  // BUD-04-03 환율 위젯: 경비 탭에만, 국내 여행(원화)은 표시하지 않음
  const widgetCurrency =
    state.status === 'done' && state.data.tripCurrency !== 'KRW' ? state.data.tripCurrency : null

  return (
    <main className={widgetCurrency ? 'budget-page budget-page--with-aside' : 'budget-page'}>
      <div className="budget-main">
        {/* BUD-01-01 불러오는 중: 빈 카드 3개(스켈레톤), 스피너 없음 */}
        {state.status === 'loading' && <Loading count={3} />}

        {/* BUD-01-01 실패 안내 */}
        {state.status === 'error' && (
          <EmptyState
            message="경비를 불러오지 못했어요."
            action={
              <Button variant="secondary" onClick={retry}>
                다시 시도
              </Button>
            }
          />
        )}

        {state.status === 'done' && (
          <>
            {state.data.items.length === 0 ? (
              // BUD-01-01 항목 없음 안내 (아래 추가 버튼은 그대로 보임)
              <div className="budget-box">
                <EmptyState message="아직 경비가 없어요. 항목을 추가해 보세요." />
              </div>
            ) : (
              // BUD-03-03 경비 탭 맨 위에는 내 1인 경비만
              <MyShareSummary myShare={state.summary.myShare} />
            )}
            <BudgetList
              data={state.data}
              commonTotal={state.summary.commonTotal}
              myPersonal={state.summary.myShare.personal}
              onAdd={(scope) => {
                toast.close()
                setForm({ scope })
              }}
              onEdit={(item) => {
                toast.close()
                setForm({ scope: item.scope, item })
              }}
              onDelete={(item) => {
                toast.close()
                setDeleteTarget(item)
              }}
            />
          </>
        )}
      </div>

      {widgetCurrency && (
        <aside className="budget-aside">
          <ExchangeRateWidget currency={widgetCurrency} />
        </aside>
      )}

      {state.status === 'done' && form && (
        <BudgetItemForm
          // 다른 항목을 열면 창을 새로 채움
          key={form.item ? `edit-${form.item.itemId}` : `add-${form.scope}`}
          tripId={tripId}
          scope={form.scope}
          editing={form.item}
          tripCurrency={state.data.tripCurrency}
          memberCount={state.data.memberCount}
          onClose={() => setForm(null)}
          onSaved={onSaved}
          // 개수 초과·권한 없음·이미 삭제됨: 화면 아래 가운데 상태 창 (닫을 때까지 남음)
          onServerError={(message) => toast.show({ message, type: 'error' })}
        />
      )}

      {/* BUD-01-04 삭제 확인 창: "'{항목명}' 경비를 삭제할까요?" [삭제] [취소] */}
      <ConfirmDialog
        open={deleteTarget !== null}
        message={deleteTarget ? `'${deleteTarget.name}' 경비를 삭제할까요?` : ''}
        confirmLabel="삭제"
        cancelLabel="취소"
        confirmLoading={deleting}
        confirmLoadingText="삭제 중..."
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </main>
  )
}
