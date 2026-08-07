import type { ReactNode } from 'react';
import type { AsyncState } from '../hooks/useAsyncData';
import { ErrorBanner } from './ErrorBanner';

interface Props<T> {
  state: AsyncState<T>;
  /** 데이터가 비었다고 판단하는 기준. 목록이면 `data.length === 0`. */
  isEmpty?: (data: T) => boolean;
  emptyMessage?: string;
  children: (data: T) => ReactNode;
}

/**
 * 로딩 / 에러 / 빈 상태 / 정상 네 가지를 한 곳에서 그린다.
 *
 * 페이지마다 손으로 분기하면 어느 하나가 빠지고, 그때 화면은 "영원히 빈 목록" 처럼 보인다.
 * 실패인지 로딩인지 데이터가 없는 건지 구분되지 않는 것이 가장 흔한 결함이다.
 */
export function AsyncBoundary<T>({ state, isEmpty, emptyMessage, children }: Props<T>) {
  if (state.loading) {
    return <p className="state state--loading">불러오는 중…</p>;
  }

  if (state.error !== null) {
    return <ErrorBanner error={state.error} onRetry={state.reload} />;
  }

  if (state.data === null) {
    return <p className="state state--empty">{emptyMessage ?? '표시할 내용이 없습니다.'}</p>;
  }

  if (isEmpty !== undefined && isEmpty(state.data)) {
    return <p className="state state--empty">{emptyMessage ?? '데이터가 없습니다.'}</p>;
  }

  return <>{children(state.data)}</>;
}
