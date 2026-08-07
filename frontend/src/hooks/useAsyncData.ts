import { useCallback, useEffect, useState } from 'react';
import { ApiError, asApiError } from '../api/client';

/**
 * 조회 훅의 공통 상태.
 *
 * 세 상태를 모두 노출하는 것이 핵심이다. `data` 만 있으면 화면은 "실패", "로딩 중",
 * "데이터가 없음" 을 구분할 수 없고 셋 다 빈 목록으로 보인다.
 */
export interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: ApiError | null;
  /** 변경 후 목록을 다시 불러오는 경로. 이게 없으면 주문했는데 재고가 그대로 보인다. */
  reload: () => void;
}

/**
 * `load` 는 반드시 `useCallback` 으로 감싼 안정된 참조여야 한다.
 * 매 렌더마다 새 함수가 들어오면 effect 가 무한히 재실행된다.
 */
export function useAsyncData<T>(load: () => Promise<T>): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [reloadCount, setReloadCount] = useState<number>(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    load()
      .then((value) => {
        if (!cancelled) setData(value);
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        // 실패했을 때 이전 data 를 남겨두면 화면에 낡은 값과 에러가 함께 뜬다. 비운다.
        setData(null);
        setError(asApiError(cause));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    // 언마운트되거나 load 가 바뀌면 이전 응답을 버린다 (늦게 도착한 응답이 새 값을 덮지 않도록).
    return () => {
      cancelled = true;
    };
  }, [load, reloadCount]);

  const reload = useCallback(() => {
    setReloadCount((count) => count + 1);
  }, []);

  return { data, loading, error, reload };
}
