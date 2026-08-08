import { useCallback } from 'react';
import { shopApi } from '../api/shop';
import type { ProductPageResponse, ProductSearchQuery } from '../types/api';
import { useAsyncData, type AsyncState } from './useAsyncData';

/**
 * `GET /api/shop/products` — 검색 · 정렬 · 페이지네이션.
 *
 * <p>**반환 타입이 `ProductResponse[]` 가 아니라 `ProductPageResponse` 다.** 이 프로젝트에서
 * 래핑된 목록은 이 경로 하나뿐이며(계약 §9.3.1 `[C-3]`), `totalPages`·`hasNext` 없이는
 * 페이지 버튼을 그릴 수 없어 훅이 `items` 만 꺼내 줄 수 없다. **unwrap 은 화면이 `.items` 를
 * 읽는 지점에서 일어나고, 배열인지는 `shopApi.searchProducts` 가 런타임에 확인한다.**
 *
 * <p>같은 `/api/shop` 아래여도 `GET /api/shop/orders` 는 순수 배열이다 — "shop 이니까 래핑"이
 * 아니라 경로마다 계약이 다르다.
 *
 * <p>쿼리 객체를 통째로 의존성에 넣지 않고 **필드를 풀어서** 넣는다. 객체 리터럴은 매 렌더마다
 * 새 참조라, 그대로 넣으면 `useAsyncData` 의 effect 가 무한히 재실행되며 요청이 끝없이 나간다.
 */
export function useShopProducts(query: ProductSearchQuery): AsyncState<ProductPageResponse> {
  const { q, sort, page, size } = query;
  const load = useCallback(
    () => shopApi.searchProducts({ q, sort, page, size }),
    [q, sort, page, size],
  );
  return useAsyncData(load);
}
