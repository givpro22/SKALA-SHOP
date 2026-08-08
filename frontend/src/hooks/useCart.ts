import { useCallback, useState } from 'react';
import { asApiError, type ApiError } from '../api/client';
import { shopApi } from '../api/shop';
import type { CartResponse } from '../types/api';
import { useAsyncData } from './useAsyncData';

export interface CartState {
  /** 조회 실패 중이면 `null` — 낡은 카트와 에러가 한 화면에 겹치지 않게 한다. */
  cart: CartResponse | null;
  loading: boolean;
  /** **조회** 실패. `AsyncBoundary` 가 이걸 그린다. */
  error: ApiError | null;
  /** **변경** 실패. 목록은 그대로 두고 배너만 띄워야 하므로 조회 실패와 분리한다. */
  mutationError: ApiError | null;
  pending: boolean;
  reload: () => void;
  /**
   * 다시 읽고 **그 값을 돌려준다.**
   *
   * `reload()` 는 상태만 갱신하고 값을 주지 않아, "다시 읽은 총액"이 당장 필요한
   * `CART_STALE` 처리에서는 쓸 수 없다. 그 흐름이 이 함수가 존재하는 유일한 이유다.
   */
  refetch: () => Promise<CartResponse | null>;
  addItem: (productId: number, quantity: number) => Promise<boolean>;
  setQuantity: (productId: number, quantity: number) => Promise<boolean>;
  removeItem: (productId: number) => Promise<boolean>;
  clear: () => Promise<boolean>;
  dismissMutationError: () => void;
}

/**
 * 장바구니 — 조회 1건 + 변경 4건을 **하나의 상태**로 다룬다.
 *
 * <h3>왜 변경 후 재조회하지 않는가</h3>
 *
 * 계약 §9.1 `[C-4]` 는 카트의 모든 변경이 **`CartResponse` 전체**를 돌려주게 만들었다.
 * 라인 하나를 지워도 `totalPrice`·`checkoutable`·`unavailableItemCount` 가 함께 바뀌기
 * 때문이며, 그래서 라인 삭제가 204 가 아니라 **200 + 본문**이다.
 *
 * 그 응답을 버리고 `reload()` 를 부르면 **계약이 그렇게 설계된 이유가 통째로 사라진다** —
 * 라운드트립이 두 번이 되고, 그 사이에 다른 탭이 카트를 바꾸면 방금 내가 한 조작과
 * 다른 결과가 화면에 뜬다. 그래서 여기서는 **변경 응답을 그대로 상태에 앉힌다.**
 *
 * <h3>왜 `useAsyncData` 를 그대로 쓰지 않고 `cart` 상태를 따로 두는가</h3>
 *
 * `useAsyncData` 의 `data` 는 조회로만 채워진다. 변경 응답을 반영할 입구가 없어서,
 * 로딩·에러 처리는 그대로 빌려 쓰되 **값의 출처를 조회와 변경 둘로 넓혔다.** 로딩 상태
 * 관리가 갈라지지 않으면서 위의 재조회 문제도 피한다.
 *
 * @param enabled 카트를 부를 수 있는 상태인가 (로그인 + `SHOPPER`). false 면 요청하지 않는다.
 *   `ADMIN` 이 부르면 403 `FORBIDDEN` 이고(§9.4.2), 비로그인이면 401 이다. 둘 다 정상적인
 *   상태이지 사용자가 고칠 오류가 아니므로 **요청 자체를 내지 않는다.**
 */
export function useCart(enabled: boolean): CartState {
  const [cart, setCart] = useState<CartResponse | null>(null);
  const [mutationError, setMutationError] = useState<ApiError | null>(null);
  const [pending, setPending] = useState(false);

  const load = useCallback(async (): Promise<CartResponse | null> => {
    if (!enabled) {
      setCart(null);
      return null;
    }
    const fresh = await shopApi.getCart();
    setCart(fresh);
    return fresh;
  }, [enabled]);

  const state = useAsyncData(load);

  /**
   * 변경 요청 하나를 감싸는 공통 실행기.
   *
   * 네 조작(담기·수량·라인 삭제·비우기)이 전부 `CartResponse` 를 돌려주므로 성공 처리가
   * 완전히 같다. 각 함수에 try/catch 를 따로 쓰면 그중 하나만 `setPending(false)` 를
   * 빠뜨리는 일이 생기고, 그 화면은 버튼이 영원히 비활성으로 남는다.
   */
  const runMutation = useCallback(
    async (action: () => Promise<CartResponse>): Promise<boolean> => {
      setPending(true);
      setMutationError(null);
      try {
        setCart(await action());
        return true;
      } catch (cause: unknown) {
        setMutationError(asApiError(cause));
        return false;
      } finally {
        setPending(false);
      }
    },
    [],
  );

  const refetch = useCallback(async (): Promise<CartResponse | null> => {
    try {
      return await load();
    } catch (cause: unknown) {
      setMutationError(asApiError(cause));
      return null;
    }
  }, [load]);

  const addItem = useCallback(
    // quantity 는 **상대값**이다 — 이미 담긴 상품이면 기존 수량에 더해진다(BR-24).
    (productId: number, quantity: number) =>
      runMutation(() => shopApi.addCartItem({ productId, quantity })),
    [runMutation],
  );

  const setQuantity = useCallback(
    // 이쪽은 **절대값**이다. 담기와 시맨틱이 정반대이므로 함수 이름부터 다르게 뒀다 —
    // 둘 다 `updateItem` 이었다면 호출부에서 어느 쪽인지 읽히지 않는다.
    (productId: number, quantity: number) =>
      runMutation(() => shopApi.updateCartItem(productId, { quantity })),
    [runMutation],
  );

  const removeItem = useCallback(
    (productId: number) => runMutation(() => shopApi.removeCartItem(productId)),
    [runMutation],
  );

  const clear = useCallback(() => runMutation(() => shopApi.clearCart()), [runMutation]);

  const dismissMutationError = useCallback(() => setMutationError(null), []);

  return {
    // 조회가 실패했으면 낡은 카트를 보여주지 않는다. 에러 배너 위에 옛 총액이 남아 있으면
    // 사용자는 그 값이 지금도 유효하다고 읽는다.
    cart: state.error !== null ? null : cart,
    loading: state.loading,
    error: state.error,
    mutationError,
    pending,
    reload: state.reload,
    refetch,
    addItem,
    setQuantity,
    removeItem,
    clear,
    dismissMutationError,
  };
}
