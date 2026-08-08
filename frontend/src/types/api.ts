/**
 * API 계약(`_workspace/01_architect_api-contract.md` §6)에서 그대로 옮긴 타입이다.
 * `_workspace/02_backend_response-shapes.md` 의 실제 응답 14케이스와 대조 확인했다.
 *
 * **여기서 이름을 손으로 바꾸면 계약이 깨진다.** 필드명 하나가 달라도 TypeScript 는
 * 잡아주지 못하고 화면에 undefined 가 뜬다.
 *
 * 확인된 사실 (실제 응답 기준):
 * - id 필드는 `productId` / `customerId` / `orderId` / `orderItemId` 다. `id` 가 아니다.
 * - `description` / `canceledAt` / `fieldErrors` 는 값이 없을 때 키가 사라지지 않고 `null` 이다.
 *   따라서 `| null` 이며 `| undefined` 가 아니다. 옵셔널(`?`)로 선언하지 않는다.
 * - 날짜는 `"2026-08-07T19:52:53"` 문자열이다. 배열도, 오프셋도, 밀리초도 없다.
 * - 엔티티의 낙관적 락 필드 `version` 은 응답에 없다. 타입에 넣지 않는다.
 * - 목록 응답은 순수 배열이다. `{content: ...}` 같은 래퍼가 없으므로 unwrap 이 필요 없다.
 */

export type OrderStatus = 'ORDERED' | 'CANCELED';

// ---------------------------------------------------------------- 응답 DTO

export interface ProductResponse {
  productId: number;
  name: string;
  description: string | null;
  price: number;
  stock: number;
  /** 상품 이미지 주소. 없으면 null 이고 화면은 대체 표시로 떨어진다. */
  imageUrl: string | null;
  createdAt: string; // ISO-8601 "yyyy-MM-ddTHH:mm:ss"
  updatedAt: string;
}

export interface CustomerResponse {
  customerId: number;
  name: string;
  email: string;
  point: number;
  createdAt: string;
  updatedAt: string;
}

export interface OrderItemResponse {
  orderItemId: number;
  productId: number;
  productName: string;
  quantity: number;
  /** 주문 시점 단가 스냅샷. 현재 product.price 와 다를 수 있다. */
  orderPrice: number;
  /** orderPrice × quantity. 서버가 응답 생성 시 계산한다. */
  subtotal: number;
}

export interface OrderResponse {
  orderId: number;
  customerId: number;
  customerName: string;
  items: OrderItemResponse[];
  totalPrice: number;
  status: OrderStatus;
  orderedAt: string;
  canceledAt: string | null;
}

// ---------------------------------------------------------------- 요청 DTO

export interface ProductCreateRequest {
  name: string;
  description?: string | null;
  price: number;
  stock: number;
  imageUrl?: string | null;
}

/** PUT 은 전체 교체 시맨틱이다. description 을 빼면 null 로 덮어쓴다. */
export type ProductUpdateRequest = ProductCreateRequest;

export interface CustomerCreateRequest {
  name: string;
  email: string;
  point?: number;
}

/** `point` 가 없다. 포인트 변경 경로는 충전과 주문/취소뿐이다(계약 §2.4). */
export interface CustomerUpdateRequest {
  name: string;
  email: string;
}

export interface PointChargeRequest {
  amount: number;
}

export interface OrderItemRequest {
  productId: number;
  quantity: number;
}

export interface OrderCreateRequest {
  customerId: number;
  items: OrderItemRequest[];
}

// ---------------------------------------------------------------- 에러

/**
 * 계약 §5 에러 코드표 18개. **서버가 내려보내는 코드만** 담는다.
 *
 * 리터럴 유니온으로 두는 이유: 분기문에서 `'INSUFICIENT_POINT'` 처럼 오타를 내면
 * 문자열 비교는 런타임에 조용히 false 가 되지만, 유니온이면 컴파일이 실패한다.
 */
export type ErrorCode =
  | ShopErrorCode
  | 'VALIDATION_ERROR'
  | 'MALFORMED_REQUEST'
  | 'TYPE_MISMATCH'
  | 'OUT_OF_STOCK'
  | 'INSUFFICIENT_POINT'
  | 'ALREADY_CANCELED'
  | 'DUPLICATE_ORDER_ITEM'
  | 'PRODUCT_NOT_FOUND'
  | 'CUSTOMER_NOT_FOUND'
  | 'ORDER_NOT_FOUND'
  | 'ENDPOINT_NOT_FOUND'
  | 'METHOD_NOT_ALLOWED'
  | 'DUPLICATE_PRODUCT_NAME'
  | 'DUPLICATE_EMAIL'
  | 'PRODUCT_IN_USE'
  | 'CUSTOMER_HAS_ORDERS'
  | 'CONCURRENT_UPDATE'
  | 'INTERNAL_ERROR'
  // 인증 (계약 §8.4)
  | 'INVALID_CREDENTIALS'
  | 'UNAUTHORIZED'
  | 'TOKEN_EXPIRED'
  | 'DUPLICATE_USERNAME';

export interface FieldError {
  field: string;
  rejectedValue: string | null;
  reason: string;
}

/** 모든 실패 응답의 단일 형태(계약 §4.1). 상태코드와 무관하게 형태가 같다. */
export interface ErrorResponse {
  code: ErrorCode;
  message: string;
  timestamp: string;
  path: string;
  fieldErrors: FieldError[] | null;
}

// ---------------------------------------------------------------- 목록 응답
// 래핑 없는 배열이다(계약 §0.2 — 예외는 `ProductPageResponse` 하나뿐이다. 아래 §9 절 참조).

export type ProductListResponse = ProductResponse[];
export type CustomerListResponse = CustomerResponse[];
export type OrderListResponse = OrderResponse[];

// ------------------------------------------------------------------ 인증 (계약 §8)

export interface LoginRequest {
  username: string;
  password: string;
}

export interface SignupRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  /** 토큰 유효 기간(초). */
  expiresIn: number;
  username: string;
}

export interface UserResponse {
  userId: number;
  username: string;
  createdAt: string;
}

// ================================================================== 구매 흐름 (계약 §9)
//
// §6 의 기존 타입은 한 줄도 바뀌지 않았다. 아래는 §9.6 에서 그대로 옮긴 **추가분**이다.

// ------------------------------------------------------ 역할 · 구매자 신원 (§9.3.2)

export type UserRole = 'ADMIN' | 'SHOPPER';

/**
 * `GET /api/shop/me` 응답.
 *
 * `GET /api/auth/me`(§8.3, `{userId, username, createdAt}` 3필드)와 **다른 엔드포인트**다.
 * 역할과 구매자 신원이 필요해진 것은 새 화면이므로 기존 응답에 필드를 더하지 않고
 * 새 엔드포인트가 나른다. `UserResponse` 에 `role` 을 추가하면 안 된다.
 *
 * `ADMIN` 이 호출하면 `customerId`·`customerName`·`point` 가 전부 `null` 이고
 * `cartItemCount` 는 `0` 이다 — 관리자에게는 구매자 프로필이 없다(§9.4.2).
 */
export interface ShopperResponse {
  userId: number;
  username: string;
  role: UserRole;
  /** 구매자 신원. 프로필이 없으면 null (키는 존재한다). */
  customerId: number | null;
  customerName: string | null;
  point: number | null;
  /** 카트 라인 수. 프로필이 없으면 0. 헤더 배지를 그리려고 카트 전체를 부르지 않게 해 준다. */
  cartItemCount: number;
}

// ------------------------------------------------------ 상품 검색 (§9.2.1 / §9.3.1)

export type ProductSort = 'LATEST' | 'PRICE_ASC' | 'PRICE_DESC';

export interface ProductSearchQuery {
  /** 앞뒤 공백은 서버가 trim 한다. 생략·빈 문자열이면 전체. `name` 만 검색하며 부분 일치·대소문자 무시. */
  q?: string;
  /** 기본 LATEST. `sort=price,asc` 같은 Spring 문법은 400 이다. */
  sort?: ProductSort;
  /** 0-based, 기본 0. 범위를 벗어나도 404 가 아니라 200 + 빈 items 다. */
  page?: number;
  /** 기본 12, 최대 50. 51 을 보내면 조용히 잘리지 않고 400 이다. */
  size?: number;
}

/**
 * **이 프로젝트에서 유일한 래핑 목록 응답이다** (계약 §9.3.1 `[호환성 쟁점 C-3]`).
 *
 * §0.2 는 "모든 목록 응답은 순수 배열"이라고 못 박았고, 페이지네이션만 그 불변식을
 * 지킬 수 없다 — `totalElements`·`hasNext` 를 배열로 표현할 방법이 없다. 그래서 예외를
 * **경로 하나로 격리했다.** `/api/products`·`/api/customers`·`/api/orders`·
 * **`/api/shop/orders`** 는 전부 순수 배열 그대로다.
 *
 * 따라서 "shop 경로니까 래핑되어 있겠지"라고 뭉뚱그리면 `GET /api/shop/orders` 에서
 * `.items` 가 `undefined` 가 된다. 래핑 여부는 경로마다 계약을 봐야 한다.
 *
 * Spring `Page<T>` 를 그대로 직렬화한 것이 아니다 — `pageable`·`sort`·`unpaged` 같은
 * 프레임워크 내부 필드는 없고 **정확히 아래 6키뿐이다.**
 */
export interface ProductPageResponse {
  items: ProductResponse[];
  /** 0-based 현재 페이지 */
  page: number;
  size: number;
  totalElements: number;
  /** `ceil(totalElements / size)`. **0건이면 0이다** (1이 아니다). */
  totalPages: number;
  /** `page + 1 < totalPages` */
  hasNext: boolean;
}

// ------------------------------------------------------ 장바구니 (§9.3.3 / §9.3.4)

export type CartItemAvailability = 'AVAILABLE' | 'INSUFFICIENT_STOCK' | 'SOLD_OUT';

export interface CartItemResponse {
  cartItemId: number;
  productId: number;
  /** 현재 상품명. 담을 때의 이름이 아니다. */
  productName: string;
  imageUrl: string | null;
  /** **현재 단가. 결제에 쓰이는 값이다.** */
  unitPrice: number;
  /** 담은 시점 단가. **참고값이며 결제에 쓰이지 않는다.** */
  unitPriceAtAdd: number;
  priceChanged: boolean;
  quantity: number;
  /** `unitPrice × quantity` — `unitPriceAtAdd` 가 아니다(BR-27). */
  subtotal: number;
  /** 조회 시점의 현재 재고. */
  stock: number;
  availability: CartItemAvailability;
}

export interface CartResponse {
  customerId: number | null;
  /** JSON 배열이다. 빈 배열 가능. 정렬은 `addedAt DESC, cartItemId DESC`. */
  items: CartItemResponse[];
  /** 라인 수 (수량 합계가 아니다). */
  totalItemCount: number;
  /** 수량 합계. */
  totalQuantity: number;
  /** 현재 단가 기준 합계. **`CheckoutRequest.expectedTotalPrice` 에 그대로 넣는 값이다.** */
  totalPrice: number;
  unavailableItemCount: number;
  /** `items` 가 비어있지 않고 모든 라인이 AVAILABLE. */
  checkoutable: boolean;
}

/** **상대값이다 — 이미 담긴 상품이면 기존 수량에 더해진다**(BR-24). */
export interface CartItemAddRequest {
  productId: number;
  quantity: number;
}

/**
 * **절대값이다 — 더하지 않고 덮어쓴다**(§9.2.4).
 *
 * `quantity: 0` 은 삭제가 아니라 400 `VALIDATION_ERROR` 다. 0 을 삭제로 받으면
 * 수량 입력창을 지웠다 다시 쓰는 흔한 조작이 라인 삭제가 된다.
 */
export interface CartItemUpdateRequest {
  quantity: number;
}

/**
 * 체크아웃 요청. **필드가 이 하나뿐이다.**
 *
 * 품목(`items`)도 `customerId` 도 보내지 않는다 — 둘 다 서버가 결정한다.
 * 클라이언트가 보낼 수 있으면 조작할 수 있고, `customerId` 를 실어 보낼 수 있으면
 * 남의 포인트로 주문이 된다(§9.0).
 *
 * `expectedTotalPrice` 는 **사용자가 화면에서 본 총액**이며 서버 재계산값과 다르면
 * 409 `CART_STALE` 이다. 선택으로 두면 프론트가 생략하는 쪽으로 수렴하고 그 순간
 * 방어가 사라지므로 필수다.
 */
export interface CheckoutRequest {
  expectedTotalPrice: number;
}

// 체크아웃 응답은 기존 `OrderResponse` 그대로다(§9.3.5). 새 타입을 만들지 않는다 —
// 만들었으면 두 개가 서서히 갈라졌을 것이다.

// ------------------------------------------------------ 에러 코드 5개 추가 (22 → 27)

/**
 * 계약 §9.5 가 추가한 5개. 위 `ErrorCode` 유니온에 합쳐져 있다.
 *
 * **구매 흐름은 새로운 실패 *원인* 을 거의 만들지 않는다**(§9.5.1). 카트 라인의 재고 부족은
 * `CART_OUT_OF_STOCK` 이 아니라 기존 `OUT_OF_STOCK` 이고, 체크아웃 포인트 부족도 기존
 * `INSUFFICIENT_POINT` 다. 원인이 같은데 코드를 새로 만들면 프론트가 같은 안내를 두 번 구현한다.
 */
export type ShopErrorCode =
  | 'CART_EMPTY'
  | 'CART_ITEM_NOT_FOUND'
  /** 409. 카트를 연 뒤 가격이 바뀐 것이며 **요청 자체는 정상이었다**(§9.5.3). */
  | 'CART_STALE'
  /** 403. **토큰은 유효하고 역할이 맞지 않는다** — 401 과 달리 다시 로그인해도 풀리지 않는다. */
  | 'FORBIDDEN'
  | 'SHOPPER_PROFILE_CONFLICT';
