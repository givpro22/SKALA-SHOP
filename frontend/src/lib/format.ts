/** 금액은 원 단위 정수다(계약 §0). 천단위 구분자는 표시할 때만 붙인다. */
export function formatKrw(value: number): string {
  return `${value.toLocaleString('ko-KR')}원`;
}

/**
 * 서버 날짜는 `"2026-08-07T19:52:53"` 형태의 로컬 시각 문자열이다.
 * 타임존 정보가 없으므로 `new Date()` 로 파싱해 다시 포맷하면 환경에 따라 시각이 밀린다.
 * 표시용으로는 문자열 그대로 다듬는 것이 안전하고 손실이 없다.
 */
export function formatDateTime(value: string): string {
  return value.replace('T', ' ');
}

/** 날짜만 필요할 때. */
export function formatDate(value: string): string {
  return value.slice(0, 10);
}

/**
 * 목록 셀용 압축 표기. `"2026-08-07T22:18:24"` → `"08-07 22:18"`.
 *
 * 표에서 전체 타임스탬프는 좁은 열에서 두 줄로 깨져 행 높이를 들쭉날쭉하게 만든다.
 * 목록에서 필요한 것은 "언제쯤"이고 연도와 초는 상세 패널에서 전체 값으로 본다.
 */
export function formatDateTimeShort(value: string): string {
  return `${value.slice(5, 10)} ${value.slice(11, 16)}`;
}
