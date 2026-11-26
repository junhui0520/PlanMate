import { useEffect, useState } from 'react';

/**
 * 값이 delay(ms) 동안 바뀌지 않으면 그때 값을 돌려준다 (자동완성 검색용).
 * 기본 300ms: 기준값 '입력을 멈추고 0.3초 뒤 검색'.
 */
export function useDebounce<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debounced;
}
