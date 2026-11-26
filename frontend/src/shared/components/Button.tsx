import type { ComponentPropsWithRef } from 'react';

export interface ButtonProps extends ComponentPropsWithRef<'button'> {
  variant?: 'primary' | 'secondary' | 'text';
  loading?: boolean;
  loadingText?: string; // 예: '만드는 중...' (부르는 쪽이 넘김)
}

/**
 * 공통 버튼. 주요(검정 채움)·보조(검정 테두리)·텍스트(밑줄) (Figma 공통 요소).
 * loading이면 비활성 + loadingText로 글자 바꿈 (TRP-01-03 '처리 중: 누른 버튼 비활성 + 버튼 글자를 ...중으로').
 * type 기본값은 'button'(실수로 폼이 제출되지 않게).
 */
export function Button({
  variant = 'primary',
  loading = false,
  loadingText,
  type = 'button',
  disabled,
  className,
  children,
  ...rest
}: ButtonProps) {
  const classes = ['pm-btn', `pm-btn--${variant}`, className].filter(Boolean).join(' ');
  return (
    <button
      {...rest}
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
    >
      {loading && loadingText ? loadingText : children}
    </button>
  );
}
