import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { ToastContext } from '../hooks/useToast';
import type { ToastApi, ToastOptions } from '../hooks/useToast';

// 기준값 '안내 메시지': 성공은 3초 뒤 자동으로 사라짐
const SUCCESS_DURATION_MS = 3000;

interface ToastState extends ToastOptions {
  id: number; // 교체될 때마다 새로 그리기 위한 값
}

export interface ToastProviderProps {
  children: ReactNode;
}

/**
 * 상태 창 Provider. 화면 아래 가운데 고정, 한 번에 1개(새로 show하면 교체) (기준값 '안내 메시지', Figma 상태 창).
 * - success: 3초 뒤 자동으로 사라짐, role="status"
 * - error: 닫거나 close()할 때까지 남음, role="alert"
 * 사용은 hooks/useToast.ts의 useToast().
 */
export function ToastProvider({ children }: ToastProviderProps) {
  const [toast, setToast] = useState<ToastState | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const idRef = useRef(0);

  const clearTimer = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const close = useCallback(() => {
    clearTimer();
    setToast(null);
  }, [clearTimer]);

  const show = useCallback(
    (options: ToastOptions) => {
      clearTimer();
      idRef.current += 1;
      const type = options.type ?? 'success';
      setToast({ ...options, type, id: idRef.current });
      if (type === 'success') {
        timerRef.current = setTimeout(() => {
          timerRef.current = null;
          setToast(null);
        }, SUCCESS_DURATION_MS);
      }
    },
    [clearTimer],
  );

  // 언마운트 때 타이머 정리
  useEffect(() => clearTimer, [clearTimer]);

  const api = useMemo<ToastApi>(() => ({ show, close }), [show, close]);

  const handleAction = () => {
    toast?.action?.onClick();
    close();
  };

  return (
    <ToastContext value={api}>
      {children}
      {toast && (
        <div
          key={toast.id}
          className="pm-toast"
          role={toast.type === 'error' ? 'alert' : 'status'}
          aria-live={toast.type === 'error' ? undefined : 'polite'}
        >
          <span className="pm-toast__message">{toast.message}</span>
          {toast.action && (
            <button type="button" className="pm-toast__action" onClick={handleAction}>
              {toast.action.label}
            </button>
          )}
          {toast.closeLabel ? (
            <button type="button" className="pm-toast__close" onClick={close}>
              {toast.closeLabel}
            </button>
          ) : (
            <button type="button" className="pm-toast__close" onClick={close} aria-label="닫기">
              <span aria-hidden="true">×</span>
            </button>
          )}
        </div>
      )}
    </ToastContext>
  );
}
