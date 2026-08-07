import { useCallback } from 'react';
import { customerApi } from '../api/customers';
import type {
  CustomerCreateRequest,
  CustomerResponse,
  CustomerUpdateRequest,
  PointChargeRequest,
} from '../types/api';
import { useAsyncData, type AsyncState } from './useAsyncData';
import { useMutation, type MutationState } from './useMutation';

/** GET /api/customers */
export function useCustomers(): AsyncState<CustomerResponse[]> {
  const load = useCallback(() => customerApi.list(), []);
  return useAsyncData(load);
}

/** GET /api/customers/{id} — `id` 가 null 이면 조회하지 않는다. */
export function useCustomer(id: number | null): AsyncState<CustomerResponse | null> {
  const load = useCallback(
    () => (id === null ? Promise.resolve(null) : customerApi.get(id)),
    [id],
  );
  return useAsyncData(load);
}

/** POST /api/customers */
export function useCreateCustomer(): MutationState<[CustomerCreateRequest], CustomerResponse> {
  return useMutation(customerApi.create);
}

/** PUT /api/customers/{id} */
export function useUpdateCustomer(): MutationState<
  [number, CustomerUpdateRequest],
  CustomerResponse
> {
  return useMutation(customerApi.update);
}

/** DELETE /api/customers/{id} — 204, 본문 없음 */
export function useDeleteCustomer(): MutationState<[number], void> {
  return useMutation(customerApi.remove);
}

/** POST /api/customers/{id}/points — 멱등하지 않다. 두 번 누르면 두 번 충전된다. */
export function useChargePoint(): MutationState<[number, PointChargeRequest], CustomerResponse> {
  return useMutation(customerApi.chargePoint);
}
