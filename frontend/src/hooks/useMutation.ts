import { useCallback, useState } from 'react';
import { ApiError, asApiError } from '../api/client';

/**
 * 변경 요청의 결과.
 *
 * "실패하면 null 을 돌려준다" 로 하지 않은 이유가 두 가지다:
 * - DELETE 는 204 라서 성공값이 `void` 다. `null` 비교로는 성공과 실패가 구분되지 않는다.
 * - 성공값 자체가 `null` 일 수 있는 API 가 나중에 생기면 조용히 실패로 읽힌다.
 *
 * 판별 유니온으로 두면 `outcome.ok` 를 확인하지 않고 `outcome.value` 에 접근하는 코드가
 * 컴파일되지 않는다.
 */
export type MutationOutcome<TResult> =
  | { ok: true; value: TResult }
  | { ok: false; error: ApiError };

export interface MutationState<TArgs extends unknown[], TResult> {
  run: (...args: TArgs) => Promise<MutationOutcome<TResult>>;
  pending: boolean;
  /** 마지막 실패. 배너 표시용. */
  error: ApiError | null;
  /** 마지막 성공 결과. 성공 배너 표시용. */
  result: TResult | null;
  reset: () => void;
}

export function useMutation<TArgs extends unknown[], TResult>(
  action: (...args: TArgs) => Promise<TResult>,
): MutationState<TArgs, TResult> {
  const [pending, setPending] = useState<boolean>(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [result, setResult] = useState<TResult | null>(null);

  const run = useCallback(
    async (...args: TArgs): Promise<MutationOutcome<TResult>> => {
      setPending(true);
      setError(null);
      setResult(null);
      try {
        const value = await action(...args);
        setResult(value);
        return { ok: true, value };
      } catch (cause: unknown) {
        const apiError = asApiError(cause);
        setError(apiError);
        return { ok: false, error: apiError };
      } finally {
        setPending(false);
      }
    },
    [action],
  );

  const reset = useCallback(() => {
    setError(null);
    setResult(null);
  }, []);

  return { run, pending, error, result, reset };
}
