import type { OrderCreateRequest, OrderResponse } from '../types/api';
import { apiGet, apiGetList, apiPost } from './client';

/** 계약 §1.3 — 주문 엔드포인트 4개. */
export const orderApi = {
  /**
   * GET /api/orders → OrderResponse[] (순수 배열)
   *
   * 정렬은 서버가 `orderedAt DESC, orderId DESC` 로 고정한다. 프론트에서 다시 정렬하지 않는다.
   * `customerId` 를 주면 해당 고객의 주문만 조회한다.
   */
  list: (customerId?: number): Promise<OrderResponse[]> =>
    apiGetList<OrderResponse>(
      customerId === undefined ? '/api/orders' : `/api/orders?customerId=${customerId}`,
    ),

  /** GET /api/orders/{id} → OrderResponse */
  get: (id: number): Promise<OrderResponse> => apiGet<OrderResponse>(`/api/orders/${id}`),

  /** POST /api/orders → 201 OrderResponse */
  create: (body: OrderCreateRequest): Promise<OrderResponse> =>
    apiPost<OrderResponse>('/api/orders', body),

  /**
   * POST /api/orders/{id}/cancel → 200 OrderResponse
   *
   * 본문이 없다. `apiPost` 에 body 를 넘기지 않으면 Content-Type 도 붙지 않는다(계약 §1.3).
   */
  cancel: (id: number): Promise<OrderResponse> =>
    apiPost<OrderResponse>(`/api/orders/${id}/cancel`),
};
