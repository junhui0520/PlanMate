import { createContext, useContext } from 'react';

/*
 * 상태 창(안내 메시지) 열기·닫기. 화면 부품은 components/Toast.tsx의 ToastProvider.
 * 기준값 '안내 메시지': 성공은 3초 뒤 자동, 실패·연결 끊김은 닫을 때까지.
 * 문구는 부르는 쪽(각 기능)이 넘긴다.
 */

export type ToastType = 'success' | 'error';

export interface ToastAction {
  label: string; // 예: '다시 시도'
  onClick: () => void;
}

export interface ToastOptions {
  message: string;
  type?: ToastType; // 기본 'success'
  action?: ToastAction;
  closeLabel?: string; // 있으면 글자 버튼, 없으면 X 아이콘
}

export interface ToastApi {
  show(options: ToastOptions): void;
  close(): void;
}

export const ToastContext = createContext<ToastApi | null>(null);

/** 사용: const toast = useToast(); toast.show({ message: '...' }) */
export function useToast(): ToastApi {
  const api = useContext(ToastContext);
  if (!api) {
    throw new Error('useToast는 ToastProvider 안에서만 쓸 수 있어요.');
  }
  return api;
}
