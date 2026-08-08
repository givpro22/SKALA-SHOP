import type {
  CartItemAddRequest,
  CartItemUpdateRequest,
  CartResponse,
  CheckoutRequest,
  OrderResponse,
  ProductPageResponse,
  ProductSearchQuery,
  ShopperResponse,
} from '../types/api';
import { ApiError, apiDeleteFor, apiGet, apiGetList, apiPost, apiPut } from './client';

/**
 * 계약 §9 — 구매 흐름 엔드포인트 **11건**.
 *
 * <p>관리자 경로(`/api/products`·`/api/customers`·`/api/orders`)와 **파일부터 나눠 두었다.**
 * 두 흐름의 결정적 차이는 `customerId` 를 누가 정하느냐이며(§9.0), shop 경로는 전부
 * **서버가 토큰에서 결정한다 — 요청에 실을 수 없다.** 같은 파일에 섞어 두면 언젠가
 * "편의상" `customerId` 파라미터가 하나 생기고, 그 순간 구매자가 남의 포인트로 주문할 수 있다.
 *
 * <p>여기 있는 함수 중 `searchProducts` 만 공개다. 나머지 10건은 토큰이 필요하고,
 * 그중 9건은 `SHOPPER` 역할까지 요구한다(`me` 만 모든 역할).
 */

/**
 * 검색 쿼리를 문자열로 만든다.
 *
 * <p>**값이 없는 파라미터는 아예 붙이지 않는다.** `?q=&sort=&page=&size=` 처럼 빈 값을 보내도
 * 서버는 기본값으로 정규화하지만(§9.2.6), 그러면 네트워크 탭과 캡처에 실제로 무엇을 검색했는지가
 * 드러나지 않는다. 붙은 파라미터만 보고 요청 의도를 읽을 수 있어야 한다.
 *
 * <p>`URLSearchParams` 를 쓰는 이유는 한글 검색어 때문이다. 손으로 문자열을 이어 붙이면
 * `q=키보드` 가 인코딩되지 않은 채 나가고, 그 실패는 브라우저마다 다르게 드러난다.
 */
function toSearchParams(query: ProductSearchQuery): string {
  const params = new URLSearchParams();
  if (query.q !== undefined && query.q.trim() !== '') params.set('q', query.q.trim());
  if (query.sort !== undefined) params.set('sort', query.sort);
  if (query.page !== undefined) params.set('page', String(query.page));
  if (query.size !== undefined) params.set('size', String(query.size));
  const encoded = params.toString();
  return encoded === '' ? '' : `?${encoded}`;
}

export const shopApi = {
  /**
   * `GET /api/shop/products` → **`ProductPageResponse` (이 프로젝트의 유일한 래핑 응답)**
   *
   * <p>**공개다.** 토큰 없이도 200 이며, 카탈로그를 비로그인에게 닫으면 로그인해야 무엇을 파는지
   * 알 수 있는 쇼핑몰이 된다.
   *
   * <p>`items` 가 배열인지 **런타임에 확인한다.** 목록이 배열인 나머지 경로는 `apiGetList` 가
   * 같은 일을 하지만(계약 §0.2), 이 경로만 래핑이라 그 방어 밖에 있다. 제네릭
   * `apiGet<ProductPageResponse>` 는 컴파일러에게 하는 약속일 뿐이라, 서버가 Spring `Page<T>`
   * 를 그대로 직렬화해 `{content: [...], pageable: {...}}` 를 내보내도 **빌드는 통과하고**
   * 화면이 `.map is not a function` 으로 죽는다. 계약이 명시적으로 금지한 실패 모양이므로
   * (§9.3.1) 프론트에서 `content` 를 unwrap 해 덮지 않고 **계약 위반으로 드러낸다.**
   */
  searchProducts: async (query: ProductSearchQuery): Promise<ProductPageResponse> => {
    const path = `/api/shop/products${toSearchParams(query)}`;
    const body = await apiGet<ProductPageResponse>(path);
    if (!Array.isArray(body.items)) {
      throw new ApiError(
        'MALFORMED_RESPONSE',
        `상품 검색 응답의 items 가 배열이 아닙니다. 계약 §9.3.1 위반입니다. (${path})`,
        200,
        null,
      );
    }
    return body;
  },

  /**
   * `GET /api/shop/me` → `ShopperResponse`
   *
   * <p>**역할(`role`)을 얻는 유일한 경로다.** `GET /api/auth/me`(§8.3)는 3필드 고정이고
   * `role` 이 없다 — 관리 메뉴를 보일지 판단하려면 이쪽을 불러야 한다.
   *
   * <p>shop 경로 중 **유일하게 `ADMIN` 도 호출할 수 있다.** 조회이며 아무것도 생성하지 않기
   * 때문이다. 다른 shop 쓰기 경로는 프로필이 없으면 `Customer` 를 자동 생성하는데(BR-31),
   * 관리자가 그 경로를 타면 관리자 이름의 고객이 생기고 자기 지갑을 자기가 충전할 수 있게 된다.
   */
  me: (): Promise<ShopperResponse> => apiGet<ShopperResponse>('/api/shop/me'),

  /** `GET /api/shop/cart` → `CartResponse`. 토큰이 없으면 **401**이다(200 + 빈 카트가 아니다). */
  getCart: (): Promise<CartResponse> => apiGet<CartResponse>('/api/shop/cart'),

  /**
   * `POST /api/shop/cart/items` → **200** `CartResponse`
   *
   * <p>**201 이 아니고 `Location` 헤더도 없다.** 라인을 개별 조회하는 경로가 없어 가리킬 URI 가
   * 없고, 같은 상품을 다시 담으면 새 라인이 아니라 **기존 라인의 수량이 는다**(BR-24) —
   * 생성이 아니라 갱신이다. `quantity` 는 **상대값**이며 담긴 수량에 더해진다.
   */
  addCartItem: (body: CartItemAddRequest): Promise<CartResponse> =>
    apiPost<CartResponse>('/api/shop/cart/items', body),

  /**
   * `PUT /api/shop/cart/items/{productId}` → 200 `CartResponse`
   *
   * <p>`quantity` 는 **절대값이다. 더하지 않는다.** 담기(POST)와 시맨틱이 정반대이며,
   * 둘을 같게 만들면 담기 버튼이 안 늘거나 수량 입력창이 입력한 값이 되지 않는다.
   *
   * <p>경로 변수가 `cartItemId` 가 아니라 **`productId`** 다.
   */
  updateCartItem: (productId: number, body: CartItemUpdateRequest): Promise<CartResponse> =>
    apiPut<CartResponse>(`/api/shop/cart/items/${productId}`, body),

  /**
   * `DELETE /api/shop/cart/items/{productId}` → **200 + `CartResponse`** (204 가 아니다)
   *
   * <p>엔티티 삭제(`/api/products/{id}`)와 달리 본문이 있다 — `[C-4]`. `apiDelete` 가 아니라
   * `apiDeleteFor` 를 쓰는 이유이며, 여기서 `apiDelete` 를 쓰면 갱신된 총액이 조용히 버려진다.
   */
  removeCartItem: (productId: number): Promise<CartResponse> =>
    apiDeleteFor<CartResponse>(`/api/shop/cart/items/${productId}`),

  /** `DELETE /api/shop/cart` → **200 + 빈 `CartResponse`**. 이미 비어 있어도 200 이다(멱등, BR-35). */
  clearCart: (): Promise<CartResponse> => apiDeleteFor<CartResponse>('/api/shop/cart'),

  /**
   * `POST /api/shop/orders` → **201** `OrderResponse` (§9.3.5 — 기존 DTO 그대로다)
   *
   * <p>보내는 것은 `expectedTotalPrice` **하나뿐이다.** 품목도 `customerId` 도 서버가 정한다.
   *
   * <p>성공하면 카트가 비워지는 것까지 **한 트랜잭션**이고, 실패하면 카트는 손대지 않은 상태로
   * 남는다(BR-29). 따라서 실패 후 프론트가 카트를 되돌리는 보상 로직을 짤 필요가 없다.
   */
  checkout: (body: CheckoutRequest): Promise<OrderResponse> =>
    apiPost<OrderResponse>('/api/shop/orders', body),

  /**
   * `GET /api/shop/orders` → **`OrderResponse[]` 순수 배열**
   *
   * <p>**같은 `/api/shop` 아래인데 `searchProducts` 와 달리 래핑이 없다.** §0.2 의 예외는
   * `GET /api/shop/products` 하나로 격리돼 있다(§9.3.1) — 개인 주문 건수는 카탈로그와 달리
   * 폭발하지 않아 페이지네이션을 넣지 않았고, 넣는 순간 §0.2 의 예외가 둘이 된다.
   * `apiGetList` 가 배열임을 런타임에 확인하므로 이 가정이 조용히 깨지지 않는다.
   *
   * <p>정렬은 서버가 `orderedAt DESC, orderId DESC` 로 고정한다. 프론트에서 다시 정렬하지 않는다.
   */
  listOrders: (): Promise<OrderResponse[]> => apiGetList<OrderResponse>('/api/shop/orders'),

  /**
   * `GET /api/shop/orders/{id}` → `OrderResponse`
   *
   * <p>**남의 주문이면 403 이 아니라 404 `ORDER_NOT_FOUND` 다.** 403 은 "그 번호의 주문이
   * 존재한다"를 알려주므로 번호를 훑어 남의 주문 존재를 확인할 수 있다. `INVALID_CREDENTIALS`
   * 가 아이디 존재 여부를 감춘 것과 같은 판단이다(§8.4).
   */
  getOrder: (id: number): Promise<OrderResponse> =>
    apiGet<OrderResponse>(`/api/shop/orders/${id}`),

  /** `POST /api/shop/orders/{id}/cancel` → 200 `OrderResponse`. 본문이 없다. */
  cancelOrder: (id: number): Promise<OrderResponse> =>
    apiPost<OrderResponse>(`/api/shop/orders/${id}/cancel`),
};
