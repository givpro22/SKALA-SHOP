import type { ReactNode } from 'react';
import type { RoleView } from '../hooks/useShopper';

interface Props {
  loggedIn: boolean;
  role: RoleView;
  children: ReactNode;
}

/**
 * 장바구니 · 주문서 · 내 주문을 감싸는 문지기.
 *
 * <p>이 세 화면은 `SHOPPER` 만 쓸 수 있다(계약 §9.4.2). **막는 것 자체보다 왜 막혔는지를
 * 세 경우로 갈라 말하는 것이 이 컴포넌트의 목적이다** — 셋은 사용자가 취할 행동이 전부 다르다.
 *
 * <table>
 *   <tr><td>비로그인</td><td>로그인하면 풀린다</td></tr>
 *   <tr><td>확인 중</td><td>기다리면 된다</td></tr>
 *   <tr><td><b>ADMIN</b></td><td><b>이 계정으로는 영원히 안 된다.</b> 다시 로그인해도 같다</td></tr>
 * </table>
 *
 * <p>셋을 "권한이 없습니다" 하나로 묶으면 관리자는 자기 비밀번호를 의심하고, 비로그인
 * 사용자는 로그인하면 된다는 것을 모른다. 401 과 403 을 나눈 것과 같은 기준이다(§9.4.3).
 *
 * <p><b>요청을 내지 않고 화면에서 갈라내는 이유:</b> 세 경우 모두 서버에 물으면 401/403 이
 * 오는데, 그건 사용자가 고칠 수 있는 오류가 아니라 **정상적인 상태**다. 에러 배너로 그리면
 * 무언가 잘못된 것처럼 보이고, 401 응답은 `client.ts` 가 토큰까지 지운다.
 */
export function ShopperGate({ loggedIn, role, children }: Props) {
  if (!loggedIn) {
    return (
      <p className="state state--empty block block--empty">
        구매 기능은 로그인 후 사용할 수 있습니다. 오른쪽 위에서 로그인해 주세요.
        <br />
        <span className="muted">상품 목록과 검색은 로그인 없이도 그대로 보입니다.</span>
      </p>
    );
  }

  if (role.unknown) {
    return <p className="state state--loading">계정 정보를 확인하는 중…</p>;
  }

  if (role.isAdmin) {
    return (
      <p className="state state--empty block block--empty" data-blocked-role="ADMIN">
        <strong>관리자 계정은 구매 기능을 사용할 수 없습니다.</strong>
        <br />
        관리자가 장바구니를 쓰면 관리자 이름의 고객이 생기고, 그 지갑을 스스로 충전해 구매할 수
        있게 됩니다. 역할을 나눈 목적 자체가 사라지므로 서버도 이 요청을{' '}
        <code>403 FORBIDDEN</code> 으로 거부합니다.
        <br />
        <span className="muted">구매자 계정(예: kim@skala.shop)으로 로그인해 주세요.</span>
      </p>
    );
  }

  return <>{children}</>;
}
