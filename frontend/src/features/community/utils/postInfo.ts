// PUB-01-01·PUB-02-05 공개 정보(공개용 제목·소개) 규칙. 공유하기 창·제목·소개 수정 창이 함께 쓴다.
import { isBlank } from '../../../shared/utils/validation'

export const POST_TITLE_MAX = 40
export const POST_INTRO_MAX = 100
export const INTRO_MIN_ROWS = 4
export const INTRO_MAX_ROWS = 10

export interface PostInfo {
  title: string
  intro: string
}

/** 서버로 보낼 값: 앞뒤 공백 제거, 소개가 비면 null */
export function toPostInfoRequest(info: PostInfo): { title: string; intro: string | null } {
  const intro = info.intro.trim()
  return { title: info.title.trim(), intro: intro === '' ? null : intro }
}

/** 처음 값과 달라졌는지 ([저장] 활성·버릴지 확인) */
export function isPostInfoChanged(a: PostInfo, b: PostInfo): boolean {
  return a.title.trim() !== b.title.trim() || a.intro.trim() !== b.intro.trim()
}

/** [다음]·[저장] 가능: 공개용 제목이 있을 때만 */
export function hasPostTitle(info: PostInfo): boolean {
  return !isBlank(info.title)
}
