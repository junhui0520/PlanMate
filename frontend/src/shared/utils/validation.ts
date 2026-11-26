/*
 * 공통 입력 검사.
 * 글자 수는 코드 포인트 기준(이모지 1자)으로 센다. Oracle CHAR 길이·Java codePointCount와 맞춘다.
 * 비밀번호·닉네임·이메일 규칙은 A 기능에서 만든다.
 */

const CONTROL_CHAR = /\p{Cc}/u;
const CONTROL_CHAR_RUN = /\p{Cc}+/gu;

/** 글자 수(코드 포인트). countChars('a😀') = 2 */
export function countChars(value: string): number {
  return Array.from(value).length;
}

/** 앞에서 max 코드 포인트만 남긴다 (기준값 '붙여넣기는 앞부분만') */
export function sliceChars(value: string, max: number): string {
  return Array.from(value).slice(0, max).join('');
}

/** 앞뒤 공백을 지운 뒤 비었는지 */
export function isBlank(value: string): boolean {
  return value.trim().length === 0;
}

/** 줄바꿈·탭 같은 제어문자가 있는지 */
export function hasControlChars(value: string): boolean {
  return CONTROL_CHAR.test(value);
}

/** 연속된 제어문자 묶음을 공백 1칸으로 바꾼다 (기준값 '제어문자 붙여넣기 처리') */
export function replaceControlChars(value: string): string {
  return value.replace(CONTROL_CHAR_RUN, ' ');
}
