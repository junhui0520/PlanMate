export interface LoadingProps {
  count?: number; // 기본 3 (TRP-02-01 빈 카드 3개)
  variant?: 'card' | 'line'; // line은 C-03 '패널에 빈 줄'용
}

/**
 * 로딩 표시. 스켈레톤만 쓰고 스피너는 쓰지 않는다 (TRP-02-01 '빈 카드 3개(스켈레톤), 스피너는 쓰지 않음').
 */
export function Loading({ count = 3, variant = 'card' }: LoadingProps) {
  return (
    <div className={`pm-skeleton pm-skeleton--${variant}`} aria-busy="true">
      {Array.from({ length: count }, (_, index) => (
        <span key={index} />
      ))}
    </div>
  );
}
