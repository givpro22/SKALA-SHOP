import { useCallback, useEffect, useState } from 'react';
import { authApi } from '../api/auth';
import { asApiError, type ApiError } from '../api/client';
import { clearToken, readToken, readUsername, saveToken } from '../lib/token';

export interface AuthState {
  username: string | null;
  loggedIn: boolean;
  pending: boolean;
  error: ApiError | null;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
  reset: () => void;
}

/**
 * 로그인 상태 하나만 들고 있는 훅.
 *
 * <p>기동 시 저장된 토큰이 있으면 `/api/auth/me` 로 **한 번 확인한다.** 확인하지 않으면
 * 만료된 토큰으로 "로그인됨" 화면이 뜨고, 사용자는 쓰기를 눌러 401 을 본 뒤에야 알게 된다.
 * 실패하면 `client.ts` 가 토큰을 지우므로 여기서는 상태만 내리면 된다.
 */
export function useAuth(): AuthState {
  const [username, setUsername] = useState<string | null>(readUsername());
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    if (readToken() === null) return;
    let alive = true;
    authApi
      .me()
      .then((user) => {
        if (alive) setUsername(user.username);
      })
      .catch(() => {
        if (alive) setUsername(null);
      });
    return () => {
      alive = false;
    };
  }, []);

  const login = useCallback(async (id: string, password: string): Promise<boolean> => {
    setPending(true);
    setError(null);
    try {
      const result = await authApi.login({ username: id, password });
      saveToken(result.accessToken, result.username);
      setUsername(result.username);
      return true;
    } catch (cause) {
      setError(asApiError(cause));
      return false;
    } finally {
      setPending(false);
    }
  }, []);

  const logout = useCallback(() => {
    clearToken();
    setUsername(null);
    setError(null);
  }, []);

  const reset = useCallback(() => setError(null), []);

  return { username, loggedIn: username !== null, pending, error, login, logout, reset };
}
