import { useCallback, useState } from 'react';
import { asApiError, type ApiError } from '../api/client';
import { shopApi } from '../api/shop';
import type { OrderResponse } from '../types/api';
import { useAsyncData, type AsyncState } from './useAsyncData';
import { useMutation, type MutationState } from './useMutation';
import type { CartState } from './useCart';

/**
 * `GET /api/shop/orders` — **순수 배열이다.** 같은 `/api/shop` 아래인 상품 검색과 달리
 * 래핑이 없다(§9.3.1 은 예외를 경로 하나로 격리했다).
 */
export function useShopOrders(enabled: boolean): AsyncState<OrderResponse[]> {
  const load = useCallback(
    () => (enabled ? shopApi.listOrders() : Promise.resolve<OrderResponse[]>([])),
    [enabled],
  );
  return useAsyncData(load);
}

/** `GET /api/shop/orders/{id}` — `id` 가 null 이면 조회하지 않는다. */
export function useShopOrder(id: number | null): AsyncState<OrderResponse | null> {
  const load = useCallback(
    () => (id === null ? Promise.resolve(null) : shopApi.getOrder(id)),
    [id],
  );
  return useAsyncData(load);
}

/**
 * `POST /api/shop/orders/{id}/cancel`
 *
 * <p>관리자 취소(`useCancelOrder`)와 달리 포인트 전후 조회를 붙이지 않는다. 구매자에게는
 * `GET /api/customers/{id}` 를 부를 권한이 없고(SHOPPER 는 403), 잔액은 `GET /api/shop/me` 가
 * 이미 나른다 — 취소 후 그것만 다시 읽으면 된다.
 */
export function useCancelShopOrder(): MutationState<[number], OrderResponse> {
  return useMutation(shopApi.cancelOrder);
}

/**
 * `CART_STALE` 화면에 필요한 두 금액 (계약 §9.5.4).
 *
 * **둘 다 있어야 한다.** 금액이 내렸다면 그대로 사면 되고 올랐다면 다시 판단해야 하는데,
 * 사용자가 취할 행동이 정반대다. 하나만 보여주면 어느 쪽인지조차 알 수 없어 아무 결정도
 * 할 수 없다 — "금액이 변경되었습니다"만 띄우는 화면이 불합격인 이유다.
 */
export interface StaleTotals {
  /** 사용자가 화면에서 보고 결제를 누른 금액. **우리가 보낸 `expectedTotalPrice` 그 값이다.** */
  expected: number;
  /** 거부 직후 카트를 다시 읽어 얻은 서버의 현재 총액. */
  current: number;
}

export interface CheckoutState {
  submit: () => Promise<void>;
  pending: boolean;
  error: ApiError | null;
  /** 성공한 주문. 주문 완료 화면이 이걸 그린다. */
  order: OrderResponse | null;
  /** `CART_STALE` 일 때만 채워진다. 그 외에는 `null`. */
  stale: StaleTotals | null;
  /** `CART_STALE` 복구 — 카트를 다시 읽고 배너를 닫는다. **재시도가 아니다.** */
  refreshCart: () => Promise<void>;
  reset: () => void;
}

/**
 * 체크아웃 — `POST /api/shop/orders`.
 *
 * <h3>두 금액을 어디서 얻는가 (§9.5.4 권고안 2)</h3>
 *
 * 서버는 `ErrorResponse` 5키 형태를 그대로 지키고(§4.1) `fieldErrors` 도 `null` 이다 —
 * `CART_STALE` 을 위해 응답 스키마를 바꾸지 않았다. 그래서 금액은 **응답에서 파싱하는 것이
 * 아니라 프론트가 갖고 있는 두 값으로 만든다:**
 *
 * <ul>
 *   <li><b>확인하신 금액</b> = 방금 우리가 보낸 `expectedTotalPrice` — 화면에 떠 있던 값 그 자체다</li>
 *   <li><b>현재 금액</b> = 거부 직후 재조회한 `CartResponse.totalPrice`</li>
 * </ul>
 *
 * **추가 비용이 0이다.** `CART_STALE` 은 카트를 다시 읽어야만 재시도할 수 있으므로 재조회는
 * 어차피 해야 하고, 그 응답이 곧 "현재 금액"이다. 서버 `message` 문자열을 파싱하는 대안도
 * 있지만(권고안 1) 그러면 프론트가 서버 문구 형식에 묶여, 메시지를 다듬는 순간 화면이 깨진다.
 *
 * <h3>왜 재시도가 아니라 새로고침인가</h3>
 *
 * 총액은 이미 달라졌다. 같은 `expectedTotalPrice` 를 다시 보내면 **반드시 같은 409 가 온다.**
 * `refreshCart()` 는 카트를 다시 읽어 화면 금액을 갱신할 뿐 주문을 보내지 않는다 —
 * 새 총액을 사용자가 보고 **다시 판단한 뒤** 결제를 누르는 것이 이 코드가 요구하는 흐름이다.
 *
 * <h3>성공하면 카트와 <b>구매자 프로필</b>을 둘 다 무효화한다</h3>
 *
 * 체크아웃 한 번이 서버에서 **세 가지**를 바꾼다 — 카트 비우기·포인트 차감·재고 차감(BR-29).
 * 그런데 화면에서 이 셋을 읽는 곳이 둘로 나뉜다: 카트 화면은 `GET /api/shop/cart`, **헤더의
 * 장바구니 배지와 포인트는 `GET /api/shop/me`** 다. 카트만 다시 읽으면 헤더는 옛 값에 머문다.
 *
 * **그 상태가 특히 나쁜 이유는 한 프레임이 스스로 모순되기 때문이다.** 주문 완료 화면 본문은
 * "장바구니는 주문과 같은 트랜잭션에서 비워졌습니다"라고 쓰는데 바로 위 헤더는 `장바구니 1` 을
 * 보인다. 서버는 처음부터 옳았고 화면만 두 시점을 섞어 그린 것인데, 사용자는 그것을 **결제가
 * 되지 않았다**는 뜻으로 읽는다. 새로고침해야 실제 값이 나오는 화면은 결제 화면으로서 실격이다.
 *
 * @param onCheckedOut 성공 직후 구매자 프로필(`/api/shop/me`)을 다시 읽는 경로. 페이지가 아니라
 *   **이 훅이 부른다** — 성공을 아는 곳이 여기뿐이고, 호출부에 맡기면 완료 화면을 그리는 경로
 *   하나만 빠뜨려도 같은 모순이 되살아난다.
 */
export function useCheckout(cart: CartState, onCheckedOut: () => void): CheckoutState {
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [stale, setStale] = useState<StaleTotals | null>(null);
  const [pending, setPending] = useState(false);

  const submit = useCallback(async (): Promise<void> => {
    const current = cart.cart;
    if (current === null) return;

    // 화면에 떠 있던 총액을 그대로 보낸다. 이 값이 실패 시 "확인하신 금액"이 된다.
    const expected = current.totalPrice;

    setPending(true);
    setError(null);
    setStale(null);
    setOrder(null);
    try {
      const created = await shopApi.checkout({ expectedTotalPrice: expected });
      setOrder(created);
      // 성공하면 서버가 같은 트랜잭션에서 카트를 비웠다(BR-29). 화면도 그 사실을 반영해야
      // "주문했는데 장바구니에 그대로 남아 있는" 상태가 되지 않는다.
      await cart.refetch();
      // 헤더의 장바구니 배지·포인트는 카트가 아니라 `/api/shop/me` 에서 온다. 위의 refetch 는
      // 그 값을 건드리지 않으므로, 여기서 함께 무효화하지 않으면 완료 화면과 헤더가 서로
      // 다른 시점을 그린다. 포인트를 `point - totalPrice` 로 계산해 넣지 않는 이유는 그 순간
      // 화면이 서버와 무관한 자체 산수를 갖게 되기 때문이다 — 잔액의 근거는 서버 하나여야 한다.
      onCheckedOut();
    } catch (cause: unknown) {
      const apiError = asApiError(cause);
      setError(apiError);

      if (apiError.code === 'CART_STALE') {
        /*
         * 실패했으므로 카트는 서버에서 손대지 않은 채 남아 있다(BR-29). 다만 **가격이
         * 바뀌었으므로 총액은 달라져 있다** — 그 새 총액이 사용자가 봐야 할 "현재 금액"이다.
         *
         * refetch 가 실패하면 두 금액 중 하나를 만들 수 없다. 그때는 stale 을 채우지 않아
         * 화면이 반쪽짜리 안내(§9.5.4 불합격 형태)를 그리는 대신 일반 배너로 떨어진다.
         */
        const fresh = await cart.refetch();
        if (fresh !== null) setStale({ expected, current: fresh.totalPrice });
      }
    } finally {
      setPending(false);
    }
  }, [cart, onCheckedOut]);

  const refreshCart = useCallback(async (): Promise<void> => {
    await cart.refetch();
    // 배너를 닫는다. 카트를 다시 읽었으니 이 실패는 해소됐고, 남겨 두면 새 총액 옆에
    // 옛 실패가 붙어 있어 지금 결제가 가능한 상태인지 읽히지 않는다.
    setError(null);
    setStale(null);
  }, [cart]);

  const reset = useCallback(() => {
    setError(null);
    setStale(null);
    setOrder(null);
  }, []);

  return { submit, pending, error, order, stale, refreshCart, reset };
}
