import { useId } from 'react';
import type { ChangeEvent, ComponentPropsWithRef } from 'react';
import { countChars, sliceChars } from '../utils/validation';

// 브라우저 maxLength는 UTF-16 단위로 세서 우리 기준(코드 포인트)과 달라 쓰지 않는다
type NativeInputProps = Omit<ComponentPropsWithRef<'input'>, 'value' | 'defaultValue' | 'onChange' | 'maxLength'>;

export interface InputProps extends NativeInputProps {
  value: string;
  onValueChange: (value: string) => void;
  label?: string;
  hint?: string;
  error?: string;
  maxChars?: number;
  showCount?: boolean; // "현재/최대" 표시 (ACC-01-01 닉네임 0/12)
  overflow?: 'block' | 'allow';
  onOverflow?: () => void;
}

/**
 * 공통 입력칸 (제어 컴포넌트).
 * - 오류와 안내를 칸 아래에 구분해서 보여 준다 (ACC-01-01).
 * - overflow 'block': maxChars를 넘으면 앞부분만 남기고 onOverflow 호출
 *   (기준값 '제한 글자 수에서 더 입력되지 않음, 붙여넣기는 앞부분만 + 안내', 안내 문구는 부르는 쪽).
 * - overflow 'allow': 자르지 않고 그대로 넘긴다 (A-P01 비밀번호 '초과값은 자르지 않고 오류').
 */
export function Input({
  value,
  onValueChange,
  label,
  hint,
  error,
  maxChars,
  showCount = false,
  overflow = 'block',
  onOverflow,
  id,
  className,
  ...rest
}: InputProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const hintId = `${inputId}-hint`;
  const errorId = `${inputId}-error`;
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ') || undefined;

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const next = event.target.value;
    if (overflow === 'block' && maxChars !== undefined && countChars(next) > maxChars) {
      onValueChange(sliceChars(next, maxChars));
      onOverflow?.();
      return;
    }
    onValueChange(next);
  };

  return (
    <div className="pm-field">
      {label && (
        <label className="pm-field__label" htmlFor={inputId}>
          {label}
        </label>
      )}
      <input
        {...rest}
        id={inputId}
        className={['pm-field__input', className].filter(Boolean).join(' ')}
        value={value}
        onChange={handleChange}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
      />
      {error && (
        <p id={errorId} className="pm-field__error">
          {error}
        </p>
      )}
      {hint && (
        <p id={hintId} className="pm-field__hint">
          {hint}
        </p>
      )}
      {showCount && maxChars !== undefined && (
        <span className="pm-field__count">
          {countChars(value)}/{maxChars}
        </span>
      )}
    </div>
  );
}
