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
