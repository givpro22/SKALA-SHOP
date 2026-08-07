import { apiGet, apiPost } from './client';
import type { LoginRequest, LoginResponse, SignupRequest, UserResponse } from '../types/api';

/** 계약 §8. 경로 prefix 는 여기서 붙인다. */
export const authApi = {
  login: (body: LoginRequest): Promise<LoginResponse> =>
    apiPost<LoginResponse>('/api/auth/login', body),

  signup: (body: SignupRequest): Promise<UserResponse> =>
    apiPost<UserResponse>('/api/auth/signup', body),

  /** 토큰의 주인. 토큰이 살아 있는지 확인하는 용도로도 쓴다. */
  me: (): Promise<UserResponse> => apiGet<UserResponse>('/api/auth/me'),
};
