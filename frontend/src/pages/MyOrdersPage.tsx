import { useState } from 'react';
import { AsyncBoundary } from '../components/AsyncBoundary';
import { ErrorBanner } from '../components/ErrorBanner';
import { useCancelShopOrder, useShopOrder, useShopOrders } from '../hooks/useShopOrders';
import { formatDateTime, formatKrw } from '../lib/format';
import type { OrderResponse } from '../types/api';

interface Props {
  enabled: boolean;
  onOrderChanged: () => void;
}

/**
 * 내 주문 내역 — `GET /api/shop/orders`.
 *
 * <p>**응답이 순수 배열이다.** 같은 `/api/shop` 아래인 상품 검색만 래핑돼 있고(§9.3.1),
 * 여기에 `.items` 를 붙이면 `undefined` 가 된다. 경로마다 계약을 봐야 하는 자리다.
 *
 * <p>목록에 남의 주문은 애초에 오지 않고, 남의 주문 id 를 직접 조회하면 403 이 아니라
 * **404 `ORDER_NOT_FOUND`** 다 — 403 은 "그 번호의 주문이 존재한다"를 알려주므로
 * 번호를 훑어 남의 주문 존재를 확인할 수 있다(§9.1).
 */
export function MyOrdersPage({ enabled, onOrderChanged }: Props) {
  const orders = useShopOrders(enabled);
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useShopOrder(detailId);
  const cancel = useCancelShopOrder();

  async function handleCancel(order: OrderResponse) {
    cancel.reset();
    const outcome = await cancel.run(order.orderId);
    if (outcome.ok) {
      orders.reload();
      if (detailId !== null) detail.reload();
      // 취소하면 포인트가 환급된다. 헤더의 잔액을 갱신하지 않으면 사용자는 환급이
      // 일어났는지 알 수 없다 — 응답(`OrderResponse`)에는 포인트가 없다.
      onOrderChanged();
    }
  }

  return (
    <section className="page">
      <header className="page__head">
        <div className="page__title">
          <p className="eyebrow">My orders</p>
          <h2>내 주문 내역</h2>
        </div>
        <button type="button" className="btn" onClick={orders.reload}>
          새로고침
        </button>
      </header>

      <div className="layout">
        <div className="layout__main">
          <AsyncBoundary
            state={orders}
            isEmpty={(list) => list.length === 0}
            emptyMessage="아직 주문한 내역이 없습니다."
          >
            {(list) => (
              <div className="lines">
                <div className="lines__head">
                  <span>주문</span>
                  <span>금액 · 상태</span>
                </div>
                {list.map((order) => (
                  <div className="line" key={order.orderId} data-order-status={order.status}>
                    <div className="pcard__body">
                      <h4 className="pcard__name">
                        #{order.orderId} · {order.items.map((item) => item.productName).join(', ')}
                      </h4>
                      <p className="pcard__meta">
                        <span className="muted">{formatDateTime(order.orderedAt)}</span>
                        <span className="pcard__stock">{order.items.length}종</span>
                      </p>
                    </div>

                    <span className="line__subtotal num">{formatKrw(order.totalPrice)}</span>

                    <span
                      className={
                        // ORDERED 는 상태 코드다 — 검정 mono 칩. `badge--point` 는 포인트
                        // 잔액(mint 컨텍스트)에 배정된 색이라 여기 쓰면 의미가 겹친다.
                        order.status === 'CANCELED' ? 'badge badge--canceled' : 'badge'
                      }
                    >
                      {order.status}
                    </span>

                    <button
                      type="button"
                      className="btn btn--sm"
                      onClick={() => setDetailId(order.orderId)}
                    >
                      상세
                    </button>

                    {/*
                      이미 CANCELED 인 주문에서도 취소 버튼을 숨기지 않는다. 숨기면 이중 취소가
                      화면에서 불가능해져 서버의 ALREADY_CANCELED 규칙이 동작하는지 확인할
                      경로가 사라진다. 관리자 주문 화면과 같은 판단이다.
                    */}
                    <button
                      type="button"
                      className="btn btn--sm btn--danger"
                      disabled={cancel.pending}
                      onClick={() => void handleCancel(order)}
                    >
                      취소
                    </button>
                  </div>
                ))}
              </div>
            )}
          </AsyncBoundary>
        </div>

        <aside className="layout__side">
          {cancel.error !== null && <ErrorBanner error={cancel.error} onDismiss={cancel.reset} />}

          {cancel.result !== null && (
            <p className="state block block--ok" role="status">
              주문 #{cancel.result.orderId} 이 취소되었습니다. 포인트가 환급되고 재고가 복원됐습니다.
            </p>
          )}

          {detailId !== null && (
            <div className="card">
              <div className="card__head">
                <h3>주문 상세</h3>
                <button type="button" className="btn btn--sm" onClick={() => setDetailId(null)}>
                  닫기
                </button>
              </div>
              <AsyncBoundary state={detail} emptyMessage="선택된 주문이 없습니다.">
                {(order) =>
                  order === null ? (
                    <p className="state state--empty">선택된 주문이 없습니다.</p>
                  ) : (
                    <>
                      <dl className="detail">
                        <dt>주문번호</dt>
                        <dd className="strong">#{order.orderId}</dd>
                        <dt>주문자</dt>
                        <dd>{order.customerName}</dd>
                        <dt>상태</dt>
                        <dd>{order.status}</dd>
                        <dt>주문 시각</dt>
                        <dd className="muted">{formatDateTime(order.orderedAt)}</dd>
                        <dt>취소 시각</dt>
                        <dd className="muted">
                          {order.canceledAt === null ? '—' : formatDateTime(order.canceledAt)}
                        </dd>
                        <dt>총 금액</dt>
                        <dd className="total num">{formatKrw(order.totalPrice)}</dd>
                      </dl>

                      <div className="lines">
                        <div className="lines__head">
                          <span>상품</span>
                          <span>금액</span>
                        </div>
                        {order.items.map((item) => (
                          <div className="line" key={item.orderItemId}>
                            <div className="pcard__body">
                              <h4 className="pcard__name">{item.productName}</h4>
                              {/* 주문 시점 단가 스냅샷이다. 현재 상품 가격과 다를 수 있다. */}
                              <p className="pcard__meta">
                                <span className="pcard__price">{formatKrw(item.orderPrice)}</span>
                                <span className="pcard__stock">× {item.quantity}</span>
                              </p>
                            </div>
                            <span className="line__subtotal num">{formatKrw(item.subtotal)}</span>
                          </div>
                        ))}
                      </div>
                    </>
                  )
                }
              </AsyncBoundary>
            </div>
          )}
        </aside>
      </div>
    </section>
  );
}
