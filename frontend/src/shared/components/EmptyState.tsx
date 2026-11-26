import type { ReactNode } from 'react';

export interface EmptyStateProps {
  message: string; // 각 기능의 빈 상태 문구
  action?: ReactNode; // 예: [여행 만들기] 버튼
}

/** 내용이 없을 때 표시. 가운데 정렬 회색 글자 */
export function EmptyState({ message, action }: EmptyStateProps) {
  return (
    <div className="pm-empty">
      <p>{message}</p>
      {action}
    </div>
  );
}
