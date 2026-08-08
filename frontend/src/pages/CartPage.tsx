import { useState } from 'react';
import { AsyncBoundary } from '../components/AsyncBoundary';
import { ErrorBanner } from '../components/ErrorBanner';
import type { CartState } from '../hooks/useCart';
import { formatKrw } from '../lib/format';
import type { CartItemAvailability, CartItemResponse } from '../types/api';

interface Props {
  cart: CartState;
  onCartChanged: () => void;
  onGoCheckout: () => void;
}

/**
 * 가용성 → 화면 표시. `Record<CartItemAvailability, …>` 라 계약에 값이 추가되면 컴파일이 깨진다.
 *
 * <p>**`AVAILABLE` 에 배지를 달지 않는 것이 의도다.** 모든 라인이 배지를 달면 정작 살 수 없는
 * 라인이 눈에 띄지 않는다. 강조는 예외에만 준다.
 */
const AVAILABILITY: Record<
  CartItemAvailability,
  { label: string | null; className: string; blocking: boolean }
> = {
  AVAILABLE: { label: null, className: '', blocking: false },
  INSUFFICIENT_STOCK: { label: '재고 부족', className: 'badge badge--warn', blocking: true },
  SOLD_OUT: { label: '품절', className: 'badge badge--warn', blocking: true },
};

/**
 * 장바구니 — 조회 1건 + 변경 4건.
 *
 * <p>화면이 보여주는 값 중 **저장돼 있지 않은 것이 셋**이다. `availability`·`checkoutable`·
 * `subtotal` 은 조회 시점에 현재 재고·현재 단가로 계산된 파생값이라, 담을 때는 살 수 있었는데
 * 지금은 아닐 수 있다. 그래서 카트를 열 때마다 이 셋이 달라질 수 있고, 화면은 그것을
 * 정상 동작으로 그려야 한다.
 */
export function CartPage({ cart, onCartChanged, onGoCheckout }: Props) {
  // 입력 중인 수량. 서버 값과 분리해 두지 않으면 타이핑 도중 값이 되돌아간다.
  const [drafts, setDrafts] = useState<Record<number, string>>({});

  async function commitQuantity(item: CartItemResponse) {
    const raw = drafts[item.productId];
    if (raw === undefined) return;
    const next = Number.parseInt(raw, 10);
    // 지운 칸에서 포커스가 빠진 것뿐이면 아무 일도 하지 않는다. 빈 값을 0으로 읽어 보내면
    // 400 이 뜨고, 사용자는 지웠다 다시 쓰려던 것뿐인데 오류를 본다.
    if (Number.isNaN(next) || next === item.quantity) {
      setDrafts((prev) => {
        const { [item.productId]: _removed, ...rest } = prev;
        return rest;
      });
      return;
    }
    // 절대값이다 — 이 값이 그대로 수량이 된다(더해지지 않는다).
    const ok = await cart.setQuantity(item.productId, next);
    if (ok) onCartChanged();
    setDrafts((prev) => {
      const { [item.productId]: _removed, ...rest } = prev;
      return rest;
    });
  }

  async function remove(productId: number) {
    // 응답이 204 가 아니라 200 + CartResponse 라, 지운 뒤 총액을 다시 부를 필요가 없다.
    const ok = await cart.removeItem(productId);
    if (ok) onCartChanged();
  }

  async function clearAll() {
    const ok = await cart.clear();
    if (ok) onCartChanged();
  }

  return (
    <section className="page">
      <header className="page__head">
        <div className="page__title">
          <p className="eyebrow">Cart</p>
          <h2>장바구니</h2>
        </div>
        <button type="button" className="btn" onClick={cart.reload}>
          새로고침
        </button>
      </header>

      {cart.mutationError !== null && (
        <ErrorBanner
          error={cart.mutationError}
          onDismiss={cart.dismissMutationError}
          onRecover={() => {
            cart.dismissMutationError();
            cart.reload();
          }}
        />
      )}

      <AsyncBoundary
        state={{
          data: cart.cart,
          loading: cart.loading,
          error: cart.error,
          reload: cart.reload,
        }}
        isEmpty={(data) => data === null || data.items.length === 0}
        emptyMessage="장바구니가 비어 있습니다. 상품 둘러보기에서 마음에 드는 상품을 담아 보세요."
      >
        {(data) =>
          data === null ? null : (
            <div className="layout">
              <div className="layout__main">
                <div className="lines">
                  <div className="lines__head">
                    <span>상품</span>
                    <span>수량 · 금액</span>
                  </div>

                  {data.items.map((item) => {
                    const availability = AVAILABILITY[item.availability];
                    return (
                      <div
                        className="line"
                        key={item.cartItemId}
                        data-availability={item.availability}
                      >
                        <div className="pcard__body">
                          <h4 className="pcard__name">{item.productName}</h4>
                          <p className="pcard__meta">
                            <span className="pcard__price">{formatKrw(item.unitPrice)}</span>
                            {availability.label !== null && (
                              <span className={availability.className}>
                                {availability.label} (재고 {item.stock})
                              </span>
                            )}
                            {/*
                              담을 때 단가와 지금 단가가 다르면 알린다. 결제에 쓰이는 것은
                              **현재 단가**이므로, 알리지 않으면 사용자가 기억하는 금액과
                              결제 금액이 말없이 달라진다. CART_STALE 이 막는 것과 같은 문제를
                              카트 화면에서 미리 드러내는 자리다.
                            */}
                            {item.priceChanged && (
                              <span className="badge badge--warn">
                                담을 때 {formatKrw(item.unitPriceAtAdd)} → 현재{' '}
                                {formatKrw(item.unitPrice)}
                              </span>
                            )}
                          </p>
                        </div>

                        <input
                          className="line__qty"
                          type="number"
                          min={1}
                          max={99}
                          aria-label={`${item.productName} 수량`}
                          value={drafts[item.productId] ?? String(item.quantity)}
                          disabled={cart.pending}
                          onChange={(event) =>
                            setDrafts((prev) => ({
                              ...prev,
                              [item.productId]: event.target.value,
                            }))
                          }
                          onBlur={() => void commitQuantity(item)}
                        />

                        {/* subtotal = unitPrice × quantity 다. unitPriceAtAdd 가 아니다(BR-27). */}
                        <span className="line__subtotal num">{formatKrw(item.subtotal)}</span>

                        <button
                          type="button"
                          className="btn btn--sm btn--danger"
                          disabled={cart.pending}
                          onClick={() => void remove(item.productId)}
                        >
                          삭제
                        </button>
                      </div>
                    );
                  })}
                </div>

                <div className="actions">
                  <button
                    type="button"
                    className="btn btn--sm"
                    disabled={cart.pending}
                    onClick={() => void clearAll()}
                  >
                    장바구니 비우기
                  </button>
                </div>
              </div>

              <aside className="layout__side">
                {/*
                  mint(컨텍스트 요약)다. 주문서의 결제 정보와 같은 lilac 을 쓰면 두 화면이
                  같은 보라 패널로 보여 탭을 넘어간 것이 드러나지 않는다.
                  구매 흐름의 색 진행: mint(담긴 것) → lilac(결제 결정) → lime(완료).
                */}
                <div className="block block--summary">
                  <h3>결제 예정 금액</h3>
                  <dl className="detail">
                    <dt>담긴 상품</dt>
                    <dd>{data.totalItemCount}종</dd>
                    <dt>총 수량</dt>
                    <dd>{data.totalQuantity}개</dd>
                    <dt>총 금액</dt>
                    <dd className="total num" data-cart-total={data.totalPrice}>
                      {formatKrw(data.totalPrice)}
                    </dd>
                  </dl>

                  {data.unavailableItemCount > 0 && (
                    <p className="note note--warn" data-unavailable={data.unavailableItemCount}>
                      지금 구매할 수 없는 상품이 <strong>{data.unavailableItemCount}개</strong>{' '}
                      있습니다. 담긴 수량이 재고를 넘었거나 품절된 상품이며, 수량을 줄이거나
                      빼야 주문할 수 있습니다.
                    </p>
                  )}

                  <div className="form__actions">
                    {/*
                      `checkoutable` 은 서버가 계산해 내려준다. 프론트가 items 를 훑어 다시
                      판정하면 규칙이 두 벌이 되고, 둘이 어긋나는 날 화면과 서버가 다른 말을 한다.
                    */}
                    <button
                      type="button"
                      className="btn btn--primary"
                      disabled={!data.checkoutable}
                      onClick={onGoCheckout}
                    >
                      주문서 작성
                    </button>
                  </div>
                  {!data.checkoutable && (
                    <p className="note muted">
                      모든 상품이 구매 가능한 상태여야 주문할 수 있습니다.
                    </p>
                  )}
                </div>
              </aside>
            </div>
          )
        }
      </AsyncBoundary>
    </section>
  );
}
