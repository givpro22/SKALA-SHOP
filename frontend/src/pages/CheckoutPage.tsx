import { AsyncBoundary } from '../components/AsyncBoundary';
import { ErrorBanner } from '../components/ErrorBanner';
import type { CartState } from '../hooks/useCart';
import { useCheckout } from '../hooks/useShopOrders';
import { formatDateTime, formatKrw } from '../lib/format';
import type { ShopperResponse } from '../types/api';

interface Props {
  cart: CartState;
  shopper: ShopperResponse | null;
  onOrdered: () => void;
  onGoOrders: () => void;
}

/**
 * 주문서 작성 → 주문 완료.
 *
 * <p>두 화면을 한 페이지로 둔 이유: 주문이 성공하면 **카트가 비워지므로**(BR-29) 주문서로
 * 돌아갈 대상이 없다. 별도 완료 페이지를 만들면 "빈 주문서"와 "완료 화면" 사이를 오갈 수
 * 있게 되고, 뒤로 가기 한 번에 사용자가 없는 카트를 결제하려 든다.
 *
 * <p>이 화면은 **열어 보는 것만으로는 카트를 소비하지 않는다.** 결제 버튼을 눌러야
 * `POST /api/shop/orders` 가 나간다.
 */
export function CheckoutPage({ cart, shopper, onOrdered, onGoOrders }: Props) {
  const checkout = useCheckout(cart);

  // 주문이 성공했으면 완료 화면만 그린다. 이 시점의 카트는 비어 있어, 주문서를 함께
  // 보여주면 방금 산 것이 사라진 빈 목록이 나란히 뜬다.
  if (checkout.order !== null) {
    const order = checkout.order;
    return (
      <section className="page">
        <header className="page__head">
          <div className="page__title">
            <p className="eyebrow">Order complete</p>
            <h2>주문이 완료되었습니다</h2>
          </div>
        </header>

        <div className="block block--ok" data-order-id={order.orderId}>
          <h3>주문번호 #{order.orderId}</h3>
          <dl className="detail">
            <dt>주문자</dt>
            <dd className="strong">{order.customerName}</dd>
            <dt>주문 시각</dt>
            <dd className="muted">{formatDateTime(order.orderedAt)}</dd>
            <dt>상태</dt>
            <dd>{order.status}</dd>
            <dt>결제 금액</dt>
            <dd className="total num">{formatKrw(order.totalPrice)}</dd>
          </dl>

          <div className="lines">
            <div className="lines__head">
              <span>주문 상품</span>
              <span>금액</span>
            </div>
            {order.items.map((item) => (
              <div className="line" key={item.orderItemId}>
                <div className="pcard__body">
                  <h4 className="pcard__name">{item.productName}</h4>
                  <p className="pcard__meta">
                    <span className="pcard__price">{formatKrw(item.orderPrice)}</span>
                    <span className="pcard__stock">× {item.quantity}</span>
                  </p>
                </div>
                <span className="line__subtotal num">{formatKrw(item.subtotal)}</span>
              </div>
            ))}
          </div>

          <p className="note">
            장바구니는 주문과 <strong>같은 트랜잭션</strong>에서 비워졌습니다. 결제·재고 차감·
            포인트 차감·카트 비우기가 함께 일어나거나 함께 취소됩니다.
          </p>

          <div className="form__actions">
            <button type="button" className="btn btn--primary" onClick={onGoOrders}>
              내 주문 내역 보기
            </button>
            <button
              type="button"
              className="btn"
              onClick={() => {
                checkout.reset();
                onOrdered();
              }}
            >
              계속 쇼핑하기
            </button>
          </div>
        </div>
      </section>
    );
  }

  const point = shopper?.point ?? null;

  return (
    <section className="page">
      <header className="page__head">
        <div className="page__title">
          <p className="eyebrow">Checkout</p>
          <h2>주문서 작성</h2>
        </div>
      </header>

      {/*
        결제 실패 배너는 **레이아웃 위, 페이지 전폭**에 둔다. 사이드(360px)에 두었더니
        `CART_STALE` 의 두 금액이 세로로 흩어져 배너 아래쪽이 뷰포트를 벗어났다 —
        §9.5.4 의 합격 조건이 "두 금액이 **한 화면에** 보이는가"이므로, 그 자리에서는
        기능이 맞아도 요건을 못 지킨다. 전폭이면 같은 내용이 절반 높이에 들어간다.

        컬러블록 배타 규칙(`.page:has(.block--alert)`)이 이 배너를 알아보고 옆의
        결제 정보 블록(라일락)을 흰 캔버스로 내리므로, 유색 블록은 여전히 하나다.
      */}
      {checkout.error !== null && (
        <ErrorBanner
          error={checkout.error}
          onDismiss={checkout.reset}
          onRecover={
            // 복구 버튼("장바구니 새로고침")이 있는 코드에만 핸들러를 준다.
            // 재시도 버튼은 어디에도 주지 않는다 — CART_STALE 에 그것을 달면 같은
            // expectedTotalPrice 가 다시 나가 반드시 다시 실패한다.
            () => void checkout.refreshCart()
          }
        >
          {checkout.stale !== null && (
            /*
             * §9.5.4 화면 요건 — **두 금액이 함께 보여야 합격이다.**
             *
             * 금액이 내렸으면 그대로 사면 되고 올랐으면 다시 판단해야 한다. 사용자가
             * 취할 행동이 정반대인데, 하나만 보여주면 어느 쪽인지조차 알 수 없어
             * 아무 결정도 할 수 없다.
             *
             * 두 값의 출처가 서로 다르다는 점이 핵심이다. `expected` 는 우리가 방금
             * 보낸 값이고 `current` 는 거부 직후 카트를 다시 읽은 값이다. 서버 message
             * 문자열을 파싱한 것이 아니므로, 서버가 문구를 다듬어도 이 화면은 깨지지 않는다.
             */
            <p
              className="refund"
              data-stale-expected={checkout.stale.expected}
              data-stale-current={checkout.stale.current}
            >
              확인하신 금액 <strong>{formatKrw(checkout.stale.expected)}</strong>
              {' → '}
              현재 <strong>{formatKrw(checkout.stale.current)}</strong>
              {' · '}
              <span className="muted">
                {checkout.stale.current > checkout.stale.expected
                  ? `${formatKrw(checkout.stale.current - checkout.stale.expected)} 올랐습니다. 계속 진행할지 다시 판단해 주세요.`
                  : `${formatKrw(checkout.stale.expected - checkout.stale.current)} 내렸습니다. 그대로 진행하셔도 됩니다.`}
              </span>
            </p>
          )}
        </ErrorBanner>
      )}

      <AsyncBoundary
        state={{
          data: cart.cart,
          loading: cart.loading,
          error: cart.error,
          reload: cart.reload,
        }}
        isEmpty={(data) => data === null || data.items.length === 0}
        emptyMessage="장바구니가 비어 있어 주문서를 만들 수 없습니다."
      >
        {(data) =>
          data === null ? null : (
            <div className="layout">
              <div className="layout__main">
                <div className="lines">
                  <div className="lines__head">
                    <span>주문 상품</span>
                    <span>금액</span>
                  </div>
                  {data.items.map((item) => (
                    <div className="line" key={item.cartItemId}>
                      <div className="pcard__body">
                        <h4 className="pcard__name">{item.productName}</h4>
                        <p className="pcard__meta">
                          <span className="pcard__price">{formatKrw(item.unitPrice)}</span>
                          <span className="pcard__stock">× {item.quantity}</span>
                        </p>
                      </div>
                      <span className="line__subtotal num">{formatKrw(item.subtotal)}</span>
                    </div>
                  ))}
                </div>
              </div>

              <aside className="layout__side">
                <div className="block block--form">
                  <h3>결제 정보</h3>
                  <dl className="detail">
                    <dt>주문자</dt>
                    <dd className="strong">{shopper?.customerName ?? '—'}</dd>
                    <dt>보유 포인트</dt>
                    <dd className="num">{point === null ? '—' : formatKrw(point)}</dd>
                    <dt>결제 금액</dt>
                    <dd className="total num" data-expected-total={data.totalPrice}>
                      {formatKrw(data.totalPrice)}
                    </dd>
                    <dt>결제 후 잔액</dt>
                    <dd className="num">
                      {point === null ? '—' : formatKrw(point - data.totalPrice)}
                    </dd>
                  </dl>

                  {point !== null && point < data.totalPrice && (
                    /*
                     * 경고는 띄우되 **버튼은 막지 않는다.** 화면의 포인트는 마지막 조회 시점
                     * 값이라 서버와 시차가 있고, 프론트가 판정하면 (a) 그 사이 충전된 멀쩡한
                     * 주문을 막거나 (b) 이미 불가능한 주문을 통과시킨다. 판정은 서버가 하고
                     * 화면은 그 결과(400 INSUFFICIENT_POINT)를 보여준다.
                     */
                    <p className="note note--warn">
                      보유 포인트가 결제 금액보다 적습니다. 주문을 시도하면 서버가{' '}
                      <code>INSUFFICIENT_POINT</code> 로 거부합니다.
                    </p>
                  )}

                  <div className="form__actions">
                    <button
                      type="button"
                      className="btn btn--primary"
                      disabled={checkout.pending || cart.pending}
                      onClick={() => void checkout.submit()}
                    >
                      {checkout.pending ? '주문 처리 중…' : `${formatKrw(data.totalPrice)} 결제하기`}
                    </button>
                  </div>

                  <p className="note muted">
                    화면에 보이는 <strong>{formatKrw(data.totalPrice)}</strong> 을 그대로 서버에
                    보냅니다. 그 사이 가격이 바뀌었다면 결제되지 않고 거부됩니다.
                  </p>
                </div>
              </aside>
            </div>
          )
        }
      </AsyncBoundary>
    </section>
  );
}
