import type {
  CustomerCreateRequest,
  CustomerResponse,
  CustomerUpdateRequest,
  PointChargeRequest,
} from '../types/api';
import { apiDelete, apiGet, apiGetList, apiPost, apiPut } from './client';

/** 계약 §1.2 — 고객 엔드포인트 6개. */
export const customerApi = {
  /** GET /api/customers → CustomerResponse[] (순수 배열) */
  list: (): Promise<CustomerResponse[]> => apiGetList<CustomerResponse>('/api/customers'),

  /** GET /api/customers/{id} → CustomerResponse */
  get: (id: number): Promise<CustomerResponse> => apiGet<CustomerResponse>(`/api/customers/${id}`),

  /** POST /api/customers → 201 CustomerResponse */
  create: (body: CustomerCreateRequest): Promise<CustomerResponse> =>
    apiPost<CustomerResponse>('/api/customers', body),

  /** PUT /api/customers/{id} → 200 CustomerResponse. point 는 이 경로로 바꿀 수 없다. */
  update: (id: number, body: CustomerUpdateRequest): Promise<CustomerResponse> =>
    apiPut<CustomerResponse>(`/api/customers/${id}`, body),

  /** DELETE /api/customers/{id} → 204, 본문 없음 */
  remove: (id: number): Promise<void> => apiDelete(`/api/customers/${id}`),

  /**
   * POST /api/customers/{id}/points → 200 CustomerResponse
   *
   * PUT 이 아니라 POST 다. 멱등하지 않으므로 두 번 호출하면 두 번 충전된다(계약 §1.2).
   */
  chargePoint: (id: number, body: PointChargeRequest): Promise<CustomerResponse> =>
    apiPost<CustomerResponse>(`/api/customers/${id}/points`, body),
};
