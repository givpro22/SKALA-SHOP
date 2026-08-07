import type {
  ProductCreateRequest,
  ProductResponse,
  ProductUpdateRequest,
} from '../types/api';
import { apiDelete, apiGet, apiGetList, apiPost, apiPut } from './client';

/** 계약 §1.1 — 상품 엔드포인트 5개. */
export const productApi = {
  /** GET /api/products → ProductResponse[] (순수 배열) */
  list: (): Promise<ProductResponse[]> => apiGetList<ProductResponse>('/api/products'),

  /** GET /api/products/{id} → ProductResponse */
  get: (id: number): Promise<ProductResponse> => apiGet<ProductResponse>(`/api/products/${id}`),

  /** POST /api/products → 201 ProductResponse */
  create: (body: ProductCreateRequest): Promise<ProductResponse> =>
    apiPost<ProductResponse>('/api/products', body),

  /** PUT /api/products/{id} → 200 ProductResponse (전체 교체) */
  update: (id: number, body: ProductUpdateRequest): Promise<ProductResponse> =>
    apiPut<ProductResponse>(`/api/products/${id}`, body),

  /** DELETE /api/products/{id} → 204, 본문 없음 */
  remove: (id: number): Promise<void> => apiDelete(`/api/products/${id}`),
};
