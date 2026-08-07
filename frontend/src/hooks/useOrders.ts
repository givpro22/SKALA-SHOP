import { useCallback } from 'react';
import { customerApi } from '../api/customers';
import { orderApi } from '../api/orders';
import type { OrderCreateRequest, OrderResponse } from '../types/api';
import { useAsyncData, type AsyncState } from './useAsyncData';
import { useMutation, type MutationState } from './useMutation';

/** GET /api/orders (customerId 선택) */
export function useOrders(customerId: number | null): AsyncState<OrderResponse[]> {
  const load = useCallback(
    () => orderApi.list(customerId === null ? undefined : customerId),
    [customerId],
  );
  return useAsyncData(load);
}

/** GET /api/orders/{id} — `id` 가 null 이면 조회하지 않는다. */
export function useOrder(id: number | null): AsyncState<OrderResponse | null> {
  const load = useCallback(
    () => (id === null ? Promise.resolve(null) : orderApi.get(id)),
    [id],
  );
  return useAsyncData(load);
}

/** POST /api/orders */
export function useCreateOrder(): MutationState<[OrderCreateRequest], OrderResponse> {
  return useMutation(orderApi.create);
}

/**
 * 주문 취소 결과 + 포인트 환급 증거.
 *
 * 취소 응답(`OrderResponse`)만으로는 환급이 실제로 일어났는지 알 수 없다 — 상태가
 * `CANCELED` 로 바뀌었다는 것만 보인다. 포인트가 정말 돌아왔는지는 고객을 다시 읽어야
 * 확인되고, 그것이 이 기능의 핵심 규칙이다.
 */
export interface CancelOutcome {
  order: OrderResponse;
  /** 취소 직전 고객 포인트 */
  pointBefore: number;
  /** 취소 직후 고객 포인트 */
  pointAfter: number;
}

/**
 * POST /api/orders/{id}/cancel + GET /api/customers/{id} × 2
 *
 * 취소 전후로 고객을 읽어 **증분**을 함께 돌려준다. 증분은 어떤 상태에서 실행해도 성립하지만
 * 절대값은 고객의 이력에 의존하므로, 판정 기준을 증분으로 둔다(도메인 스펙 §6.5).
 */
export function useCancelOrder(): MutationState<[OrderResponse], CancelOutcome> {
  const cancelWithRefundEvidence = useCallback(
    async (order: OrderResponse): Promise<CancelOutcome> => {
      const before = await customerApi.get(order.customerId);
      // 실패하면(ALREADY_CANCELED 등) 여기서 던져지고 아래 조회는 실행되지 않는다.
      const canceled = await orderApi.cancel(order.orderId);
      const after = await customerApi.get(order.customerId);
      return { order: canceled, pointBefore: before.point, pointAfter: after.point };
    },
    [],
  );
  return useMutation(cancelWithRefundEvidence);
}
