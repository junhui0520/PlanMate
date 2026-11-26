/*
 * 날짜·시각 표시 형식 (기능정의서 기준값 '날짜 표시'·'시간 표시'·'기록 시각 표시'·'관리자 목록 시각 표시').
 * 서버 값은 직접 잘라서 '로컬 시각'으로 만든다.
 * - new Date('2026-10-30')은 UTC로 해석돼 하루가 밀릴 수 있어 쓰지 않는다.
 * - 서버 기록 시각은 오프셋 없는 서버(한국) 시각이다 (contracts/common.yaml LocalDateTime).
 * '1일차 10.20(월)' 같은 기능별 형식은 각 파트가 만든다.
 */

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(\.\d{1,9})?$/;
const HH_MM_PATTERN = /^([01]\d|2[0-3]):([0-5]\d)$/;

interface LocalParts {
  year: number;
  month: number; // 1~12
  day: number;
  hour: number;
  minute: number;
}

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}

// 'YYYY-MM-DD' 또는 'YYYY-MM-DDTHH:mm:ss(.소수)'를 숫자로 나눈다. 형식이 다르면 null
function parseParts(value: string): LocalParts | null {
  const dateTime = DATE_TIME_PATTERN.exec(value);
  if (dateTime) {
    return {
      year: Number(dateTime[1]),
      month: Number(dateTime[2]),
      day: Number(dateTime[3]),
      hour: Number(dateTime[4]),
      minute: Number(dateTime[5]),
    };
  }
  const date = DATE_PATTERN.exec(value);
  if (date) {
    return { year: Number(date[1]), month: Number(date[2]), day: Number(date[3]), hour: 0, minute: 0 };
  }
  return null;
}

/** "2026-10-30" -> "2026.10.30(금)" (기준값 '날짜 표시'). 형식이 다르면 받은 값 그대로 */
export function formatDate(date: string): string {
  const parts = parseParts(date);
  if (!parts) return date;
  const weekday = WEEKDAYS[new Date(parts.year, parts.month - 1, parts.day).getDay()];
  return `${parts.year}.${pad2(parts.month)}.${pad2(parts.day)}(${weekday})`;
}

/**
 * "14:05" -> "오후 02:05" (기준값 '시간 표시' 12시간제).
 * 시는 01~12 두 자리, 00시는 "오전 12", 12시는 "오후 12". 형식이 다르면 받은 값 그대로
 */
export function formatTime(hhmm: string): string {
  const match = HH_MM_PATTERN.exec(hhmm);
  if (!match) return hhmm;
  const hour = Number(match[1]);
  const period = hour < 12 ? '오전' : '오후';
  const hour12 = hour % 12 === 0 ? 12 : hour % 12;
  return `${period} ${pad2(hour12)}:${match[2]}`;
}

/**
 * 기록 시각 표시 (기준값 '기록 시각 표시' + '시간 표시 12시간제(11/01, 전체)', A-16 알림함과 같음).
 * 오늘 -> "오후 02:05", 올해 다른 날 -> "10.28", 다른 해 -> "2025.12". 형식이 다르면 받은 값 그대로
 */
export function formatRecordedAt(dateTime: string, now: Date = new Date()): string {
  const parts = parseParts(dateTime);
  if (!parts) return dateTime;
  const isToday =
    parts.year === now.getFullYear() && parts.month === now.getMonth() + 1 && parts.day === now.getDate();
  if (isToday) return formatTime(`${pad2(parts.hour)}:${pad2(parts.minute)}`);
  if (parts.year === now.getFullYear()) return `${pad2(parts.month)}.${pad2(parts.day)}`;
  return `${parts.year}.${pad2(parts.month)}`;
}

/** 관리자 목록 시각 표시 (기준값 '관리자 목록 시각 표시'): 올해 -> "10.28", 아니면 "2025.12" */
export function formatAdminListDate(dateTime: string, now: Date = new Date()): string {
  const parts = parseParts(dateTime);
  if (!parts) return dateTime;
  if (parts.year === now.getFullYear()) return `${pad2(parts.month)}.${pad2(parts.day)}`;
  return `${parts.year}.${pad2(parts.month)}`;
}

/** 서버로 보낼 날짜: 로컬 기준 "2026-11-25" */
export function toIsoDate(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}
