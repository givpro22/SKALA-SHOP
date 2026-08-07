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
  | 'INTERNAL_ERROR';

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
// 래핑 없는 배열이다(계약 §0.2).

export type ProductListResponse = ProductResponse[];
export type CustomerListResponse = CustomerResponse[];
export type OrderListResponse = OrderResponse[];
