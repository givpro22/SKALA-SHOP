/**
 * 토큰 보관소.
 *
 * `localStorage` 를 쓴다. 이 선택은 **XSS 에 취약하다** — 스크립트가 주입되면 토큰을
 * 그대로 읽어 갈 수 있다. httpOnly 쿠키가 그 점에서는 안전하지만, 프론트와 API 가 서로
 * 다른 오리진에서 도는 이 구성에서는 SameSite·CORS credentials 설정이 함께 늘어난다.
 * 과제 범위에서 인증 흐름을 보이는 것이 목적이라 단순한 쪽을 택했고, 그 한계를
 * 계약 §8.6 과 QA 리포트에 명시해 둔다.
 *
 * 저장 위치를 이 파일 하나로 좁혀 둔 이유는 나중에 바꿀 때 여기만 고치면 되게 하기 위해서다.
 */
const KEY = 'skala-shop.accessToken';
const USER_KEY = 'skala-shop.username';

export function readToken(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    // 시크릿 모드나 저장소 차단 환경. 로그인은 못 하지만 조회 화면은 살아 있어야 한다.
    return null;
  }
}

export function readUsername(): string | null {
  try {
    return localStorage.getItem(USER_KEY);
  } catch {
    return null;
  }
}

export function saveToken(token: string, username: string): void {
  try {
    localStorage.setItem(KEY, token);
    localStorage.setItem(USER_KEY, username);
  } catch {
    // 저장에 실패해도 이번 세션의 메모리 상태는 유지된다. 새로고침하면 풀린다.
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(KEY);
    localStorage.removeItem(USER_KEY);
  } catch {
    /* 지울 수 없으면 그대로 둔다 — 만료된 토큰은 서버가 거부한다 */
  }
}
