import { useState } from 'react';
import type { ProductResponse } from '../types/api';

interface Props {
  product: ProductResponse;
  /** 카드용 큰 썸네일이 기본. 목록 행·사이드 패널에서는 'sm'. */
  size?: 'md' | 'sm';
}

/**
 * 상품 이미지. 없거나 못 불러오면 **대체 표시로 떨어진다.**
 *
 * <p>대체 표시가 필요한 이유는 두 가지다. `imageUrl` 은 선택 필드라 비어 있을 수 있고,
 * 값이 있어도 외부 URL 이면 오프라인·차단 환경에서 못 불러온다. 이때 깨진 이미지
 * 아이콘이 뜨면 "상품이 잘못됐다"로 읽히는데, 실제로는 그림만 없는 정상 상품이다.
 *
 * <p>대체 표시는 상품명 첫 글자를 파스텔 블록에 올린다. 색은 상품 id 로 고르므로
 * 같은 상품은 항상 같은 색이다 — 목록을 다시 불러와도 색이 튀지 않는다.
 */
export function ProductThumb({ product, size = 'md' }: Props) {
  const [failed, setFailed] = useState(false);
  const url = product.imageUrl;
  const className = size === 'sm' ? 'thumb thumb--sm' : 'thumb';

  if (url !== null && url !== '' && !failed) {
    return (
      <img
        className={className}
        src={url}
        alt=""
        loading="lazy"
        // alt 를 비운 것은 의도다. 상품명이 바로 옆에 텍스트로 있어, 이미지까지 읽으면
        // 스크린리더에서 같은 이름이 두 번 나온다.
        onError={() => setFailed(true)}
      />
    );
  }

  // 파스텔 6색을 순환한다. 토큰과 같은 값이며 CSS 변수로 참조한다.
  const tone = product.productId % 6;
  return (
    <div className={`${className} thumb--empty`} data-tone={tone} aria-hidden="true">
      <span>{product.name.slice(0, 1)}</span>
    </div>
  );
}
