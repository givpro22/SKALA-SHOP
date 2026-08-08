import { useCallback } from 'react';
import { shopApi } from '../api/shop';
import type { ShopperResponse, UserRole } from '../types/api';
import { useAsyncData, type AsyncState } from './useAsyncData';

/**
 * `GET /api/shop/me` — **역할을 얻는 유일한 경로다.**
 *
 * <p>`useAuth()` 는 "로그인했는가"만 안다(`/api/auth/me` 는 3필드 고정이고 `role` 이 없다).
 * 화면이 관리 메뉴를 보일지, 장바구니를 보일지 판단하려면 이쪽이 필요하다.
 *
 * <p>**`loggedIn` 이 false 면 호출하지 않는다.** 이 엔드포인트는 인증이 필요하므로
 * 비로그인 상태에서 부르면 401 이 나고, `client.ts` 가 그 응답에서 토큰을 지운다 —
 * 지울 토큰도 없는데 실패 배너만 남는다. 조회가 공개인 앱에서 비로그인은 정상 상태이지
 * 오류가 아니다.
 *
 * @param loggedIn 토큰이 있는가. false 면 요청 없이 `null` 을 돌려준다.
 */
export function useShopper(loggedIn: boolean): AsyncState<ShopperResponse | null> {
  const load = useCallback(
    () => (loggedIn ? shopApi.me() : Promise.resolve(null)),
    [loggedIn],
  );
  return useAsyncData(load);
}

/**
 * 화면이 실제로 묻는 질문으로 역할을 좁힌다.
 *
 * <p>페이지마다 `shopper.data?.role === 'SHOPPER'` 를 쓰면 **아직 안 불러온 상태(`null`)와
 * 관리자를 구분하지 못하는 코드가 반드시 하나 생긴다.** 둘 다 "SHOPPER 가 아님"으로 읽히는데,
 * 전자는 잠깐 기다리면 되고 후자는 영원히 아니다 — 화면이 취할 행동이 다르다.
 */
export interface RoleView {
  /** 아직 **한 번도** 판정하지 못했다 (미로그인이거나 첫 응답 대기 중). */
  unknown: boolean;
  isAdmin: boolean;
  isShopper: boolean;
  role: UserRole | null;
}

/**
 * **`loading` 을 보지 않는다 — `shopper === null` 인지만 본다.**
 *
 * <p>예전에는 `loading || shopper === null` 이었는데, 그러면 `reload()` 로 헤더를 갱신할 때마다
 * 역할이 잠깐 `unknown` 으로 되돌아간다. 그 사이 `ShopperGate` 는 자식을 "계정 정보를 확인하는
 * 중…" 으로 갈아치우므로 **장바구니·주문서·내 주문 화면이 통째로 언마운트되고 그 안의 상태가
 * 사라진다.** 체크아웃에서는 방금 받은 주문 완료 화면이 그렇게 날아갔다.
 *
 * <p>구분해야 하는 것은 두 가지다 — **아직 모르는 것**(첫 응답 대기)과 **다시 확인하는
 * 것**(재조회). 후자에는 이미 답이 있고, 역할은 같은 계정이 로그인해 있는 동안 바뀌지 않는다.
 * 로그아웃하면 `useShopper` 가 `null` 을 채우므로 `unknown` 으로 정확히 되돌아간다.
 */
export function toRoleView(shopper: ShopperResponse | null): RoleView {
  if (shopper === null) {
    return { unknown: true, isAdmin: false, isShopper: false, role: null };
  }
  return {
    unknown: false,
    isAdmin: shopper.role === 'ADMIN',
    isShopper: shopper.role === 'SHOPPER',
    role: shopper.role,
  };
}
