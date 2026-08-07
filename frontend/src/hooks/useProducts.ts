import { useCallback } from 'react';
import { productApi } from '../api/products';
import type {
  ProductCreateRequest,
  ProductResponse,
  ProductUpdateRequest,
} from '../types/api';
import { useAsyncData, type AsyncState } from './useAsyncData';
import { useMutation, type MutationState } from './useMutation';

/** GET /api/products */
export function useProducts(): AsyncState<ProductResponse[]> {
  const load = useCallback(() => productApi.list(), []);
  return useAsyncData(load);
}

/**
 * GET /api/products/{id}
 *
 * `id` 가 `null` 이면 조회하지 않는다. 상세 패널이 닫혀 있을 때 호출하지 않기 위한 것이다.
 */
export function useProduct(id: number | null): AsyncState<ProductResponse | null> {
  const load = useCallback(
    () => (id === null ? Promise.resolve(null) : productApi.get(id)),
    [id],
  );
  return useAsyncData(load);
}

/** POST /api/products */
export function useCreateProduct(): MutationState<[ProductCreateRequest], ProductResponse> {
  return useMutation(productApi.create);
}

/** PUT /api/products/{id} */
export function useUpdateProduct(): MutationState<[number, ProductUpdateRequest], ProductResponse> {
  return useMutation(productApi.update);
}

/** DELETE /api/products/{id} — 204, 본문 없음 */
export function useDeleteProduct(): MutationState<[number], void> {
  return useMutation(productApi.remove);
}
