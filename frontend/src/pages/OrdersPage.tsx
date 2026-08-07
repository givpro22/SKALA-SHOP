import { useState, type FormEvent } from 'react';
import { AsyncBoundary } from '../components/AsyncBoundary';
import { ErrorBanner } from '../components/ErrorBanner';
import { useCustomers } from '../hooks/useCustomers';
import { useCancelOrder, useCreateOrder, useOrder, useOrders } from '../hooks/useOrders';
import { useProducts } from '../hooks/useProducts';
import { formatDateTime, formatKrw } from '../lib/format';
import type { OrderItemRequest, OrderResponse, ProductResponse } from '../types/api';

interface OrderLine {
  productId: string;
  quantity: string;
}

const EMPTY_LINE: OrderLine = { productId: '', quantity: '1' };

function findProduct(products: ProductResponse[], productId: string): ProductResponse | null {
  const parsed = Number.parseInt(productId, 10);
  if (Number.isNaN(parsed)) return null;
  return products.find((product) => product.productId === parsed) ?? null;
}

/** 폼 입력(문자열)을 요청 DTO 로 옮긴다. 값이 비어 있는 줄은 버린다. */
function buildItems(lines: OrderLine[]): OrderItemRequest[] {
  const items: OrderItemRequest[] = [];
  for (const line of lines) {
    const productId = Number.parseInt(line.productId, 10);
    const quantity = Number.parseInt(line.quantity, 10);
    if (Number.isNaN(productId) || Number.isNaN(quantity)) continue;
    items.push({ productId, quantity });
  }
  return items;
}

export function OrdersPage() {
  const products = useProducts();
  const customers = useCustomers();

  const [filterCustomerId, setFilterCustomerId] = useState<number | null>(null);
  const orders = useOrders(filterCustomerId);

  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useOrder(detailId);

  const [customerId, setCustomerId] = useState<string>('');
  const [lines, setLines] = useState<OrderLine[]>([EMPTY_LINE]);

  const createOrder = useCreateOrder();
  const cancelOrder = useCancelOrder();

  const productList = products.data ?? [];
  const customerList = customers.data ?? [];

  const selectedCustomer =
    customerList.find((customer) => String(customer.customerId) === customerId) ?? null;

  // 현재 단가 기준 예상 총액. 서버가 확정하는 값은 주문 시점 스냅샷이므로 어디까지나 참고다.
  const estimatedTotal = lines.reduce((sum, line) => {
    const product = findProduct(productList, line.productId);
    const quantity = Number.parseInt(line.quantity, 10);
    if (product === null || Number.isNaN(quantity)) return sum;
    return sum + product.price * quantity;
  }, 0);

  function updateLine(index: number, patch: Partial<OrderLine>) {
    setLines(lines.map((line, i) => (i === index ? { ...line, ...patch } : line)));
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const parsedCustomerId = Number.parseInt(customerId, 10);
    if (Number.isNaN(parsedCustomerId)) return;

    const items = buildItems(lines);
    if (items.length === 0) return;

    // 재고·포인트 부족을 프론트에서 미리 막지 않는다. 판정 주체는 서버다.
    // 미리 막으면 (a) 화면과 DB 사이의 시차 때문에 틀린 판정을 내리고,
    // (b) 비즈니스 규칙이 실제로 동작하는지 확인할 길이 사라진다.
    const outcome = await createOrder.run({ customerId: parsedCustomerId, items });

    if (outcome.ok) {
      setLines([EMPTY_LINE]);
      orders.reload();
      // 재고와 포인트가 함께 줄었다. 다시 읽지 않으면 화면에 낡은 값이 남는다.
      products.reload();
      customers.reload();
    }
  }

  async function handleCancel(order: OrderResponse) {
    const outcome = await cancelOrder.run(order);
    if (outcome.ok) {
      orders.reload();
      products.reload();
      customers.reload();
      if (detailId !== null) detail.reload();
    }
  }

  const cancelOutcome = cancelOrder.result;

  return (
    <section className="page">
      <header className="page__head">
        <div className="page__title">
          <p className="eyebrow">Orders</p>
          <h2>주문</h2>
        </div>
        <button
          type="button"
          className="btn"
          onClick={() => {
            orders.reload();
            products.reload();
            customers.reload();
          }}
        >
          새로고침
        </button>
      </header>

      <div className="layout">
        <div className="layout__main">
          {/* 입력이 결과를 만드는 곳 — lilac 컬러블록 */}
          <div className="block block--form">
            <h3>주문 생성</h3>
            <form className="form" onSubmit={(event) => void handleCreate(event)}>
              <label>
                고객
                <select
                  required
                  value={customerId}
                  onChange={(event) => setCustomerId(event.target.value)}
                >
                  <option value="">고객을 선택하세요</option>
                  {customerList.map((customer) => (
                    <option key={customer.customerId} value={String(customer.customerId)}>
                      {customer.customerId}. {customer.name} — 보유 {formatKrw(customer.point)}
                    </option>
                  ))}
                </select>
              </label>

              {selectedCustomer !== null && (
                <p className="note">
                  보유 포인트{' '}
                  <strong className="badge badge--point">
                    {formatKrw(selectedCustomer.point)}
                  </strong>
                </p>
              )}

              <div className="lines">
                <div className="lines__head">
                  <span>주문 항목</span>
                  <button
                    type="button"
                    className="btn btn--sm"
                    onClick={() => setLines([...lines, { ...EMPTY_LINE }])}
                  >
                    + 항목 추가
                  </button>
                </div>

                {lines.map((line, index) => {
                  const product = findProduct(productList, line.productId);
                  const quantity = Number.parseInt(line.quantity, 10);
                  const overStock =
                    product !== null && !Number.isNaN(quantity) && quantity > product.stock;

                  return (
                    // 주문 항목 줄은 서버 id 가 없어 순서로만 식별된다. 입력값은 전부 lines
                    // 배열이 들고 있고 렌더는 그 배열에서만 나오므로, 중간 삭제로 키가 밀려도
                    // 값이 다른 줄에 붙는 일이 없다.
                    <div className="line" key={index}>
                      <select
                        value={line.productId}
                        onChange={(event) => updateLine(index, { productId: event.target.value })}
                      >
                        <option value="">상품 선택</option>
                        {productList.map((option) => (
                          <option key={option.productId} value={String(option.productId)}>
                            {option.productId}. {option.name} — {formatKrw(option.price)} (재고{' '}
                            {option.stock})
                          </option>
                        ))}
                      </select>
                      <input
                        type="number"
                        min={1}
                        className="line__qty"
                        value={line.quantity}
                        onChange={(event) => updateLine(index, { quantity: event.target.value })}
                      />
                      <span className="line__subtotal">
                        {product !== null && !Number.isNaN(quantity)
                          ? formatKrw(product.price * quantity)
                          : '—'}
                      </span>
                      {overStock && (
                        <span className="badge badge--warn">재고 {product.stock}개</span>
                      )}
                      {lines.length > 1 && (
                        <button
                          type="button"
                          className="btn btn--sm btn--danger"
                          onClick={() => setLines(lines.filter((_, i) => i !== index))}
                        >
                          삭제
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>

              <div className="total">
                <span>예상 총액</span>
                <strong>{formatKrw(estimatedTotal)}</strong>
              </div>

              {selectedCustomer !== null && estimatedTotal > selectedCustomer.point && (
                <p className="note note--warn">
                  보유 포인트보다 주문 총액이 큽니다. 서버가 거부할 수 있습니다.
                </p>
              )}

              <button type="submit" className="btn btn--primary" disabled={createOrder.pending}>
                {createOrder.pending ? '주문 처리 중…' : '주문하기'}
              </button>
            </form>

            {createOrder.result !== null && (
              <div className="banner banner--success block block--ok" role="status">
                <div className="banner__head">
                  <span className="badge badge--ok">201 ORDERED</span>
                  <strong className="banner__title">주문이 완료되었습니다</strong>
                </div>
                <p>
                  주문번호 <strong>#{createOrder.result.orderId}</strong> ·{' '}
                  {createOrder.result.customerName} · 총액{' '}
                  <strong>{formatKrw(createOrder.result.totalPrice)}</strong>
                </p>
                <ul className="banner__fields">
                  {createOrder.result.items.map((item) => (
                    <li key={item.orderItemId}>
                      {item.productName} × {item.quantity} = {formatKrw(item.subtotal)}
                    </li>
                  ))}
                </ul>
                <p className="muted">주문 시각 {formatDateTime(createOrder.result.orderedAt)}</p>
              </div>
            )}

            {createOrder.error !== null && (
              <ErrorBanner
                error={createOrder.error}
                // CONCURRENT_UPDATE 처럼 재시도 가능한 코드일 때만 버튼이 그려진다.
                // 같은 요청을 그대로 다시 보낸다 — 서버 상태는 요청 이전과 동일하므로
                // 되돌릴 것이 없다(계약 §5.1).
                onRetry={() => {
                  const parsedCustomerId = Number.parseInt(customerId, 10);
                  if (Number.isNaN(parsedCustomerId)) return;
                  const items = buildItems(lines);
                  if (items.length === 0) return;
                  void createOrder.run({ customerId: parsedCustomerId, items });
                }}
                onDismiss={createOrder.reset}
              />
            )}
          </div>
        </div>

        <aside className="layout__side">
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
                        <dt>orderId</dt>
                        <dd>#{order.orderId}</dd>
                        <dt>고객</dt>
                        <dd className="strong">
                          {order.customerName} (id: {order.customerId})
                        </dd>
                        <dt>상태</dt>
                        <dd>
                          <span
                            className={
                              order.status === 'ORDERED'
                                ? 'badge badge--ok'
                                : 'badge badge--canceled'
                            }
                          >
                            {order.status}
                          </span>
                        </dd>
                        <dt>총액</dt>
                        <dd className="strong">{formatKrw(order.totalPrice)}</dd>
                        <dt>주문 시각</dt>
                        <dd className="muted">{formatDateTime(order.orderedAt)}</dd>
                        <dt>취소 시각</dt>
                        <dd className="muted">
                          {order.canceledAt === null ? '—' : formatDateTime(order.canceledAt)}
                        </dd>
                      </dl>
                      <table className="table table--compact">
                        <thead>
                          <tr>
                            <th>상품</th>
                            <th className="num">수량</th>
                            <th className="num">주문 시점 단가</th>
                            <th className="num">소계</th>
                          </tr>
                        </thead>
                        <tbody>
                          {order.items.map((item) => (
                            <tr key={item.orderItemId}>
                              <td data-label="상품">{item.productName}</td>
                              <td className="num" data-label="수량">
                                {item.quantity}
                              </td>
                              <td className="num" data-label="주문 시점 단가">
                                {formatKrw(item.orderPrice)}
                              </td>
                              <td className="num" data-label="소계">
                                {formatKrw(item.subtotal)}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </>
                  )
                }
              </AsyncBoundary>
            </div>
          )}

          {/*
            흰 캔버스 카드다. 이 패널은 데이터를 보여주기만 하고 사용자에게 말을 걸지
            않는다. 컬러블록으로 만들면 바로 아래 navy 블록과 한 뷰포트에 함께 잡혀
            둘 다 죽는다(390px 기준 두 블록 간격 72px, 뷰포트 844px).
            mint 의 "포인트 잔액" 역할은 .badge--point 가 이어받는다.
          */}
          <div className="card">
            <h3>상품 재고</h3>
            <AsyncBoundary
              state={products}
              isEmpty={(list) => list.length === 0}
              emptyMessage="상품이 없습니다."
            >
              {(list) => (
                <table className="table table--compact">
                  <thead>
                    <tr>
                      <th>상품</th>
                      <th className="num">단가</th>
                      <th className="num">재고</th>
                    </tr>
                  </thead>
                  <tbody>
                    {list.map((product) => (
                      <tr key={product.productId}>
                        <td data-label="상품">{product.name}</td>
                        <td className="num" data-label="단가">
                          {formatKrw(product.price)}
                        </td>
                        <td className="num" data-label="재고">
                          <span className={product.stock <= 2 ? 'badge badge--warn' : undefined}>
                            {product.stock}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </AsyncBoundary>
          </div>
        </aside>
      </div>

      <div className="card">
        <div className="card__head">
          <h3>주문 목록</h3>
          <label className="inline-field">
            고객 필터
            <select
              value={filterCustomerId === null ? '' : String(filterCustomerId)}
              onChange={(event) =>
                setFilterCustomerId(
                  event.target.value === '' ? null : Number.parseInt(event.target.value, 10),
                )
              }
            >
              <option value="">전체</option>
              {customerList.map((customer) => (
                <option key={customer.customerId} value={String(customer.customerId)}>
                  {customer.name}
                </option>
              ))}
            </select>
          </label>
        </div>

        {cancelOutcome !== null && (
          <div className="banner banner--success block block--ok" role="status">
            <div className="banner__head">
              <span className="badge badge--ok">200 CANCELED</span>
              <strong className="banner__title">주문이 취소되었습니다</strong>
            </div>
            <p>
              주문 <strong>#{cancelOutcome.order.orderId}</strong> ·{' '}
              {cancelOutcome.order.customerName} · 취소 금액{' '}
              <strong>{formatKrw(cancelOutcome.order.totalPrice)}</strong>
            </p>
            <p className="refund">
              포인트 환급: {formatKrw(cancelOutcome.pointBefore)} →{' '}
              <strong>{formatKrw(cancelOutcome.pointAfter)}</strong>{' '}
              <span className="badge badge--ok">
                +{formatKrw(cancelOutcome.pointAfter - cancelOutcome.pointBefore)}
              </span>
            </p>
            {cancelOutcome.order.canceledAt !== null && (
              <p className="muted">
                취소 시각 {formatDateTime(cancelOutcome.order.canceledAt)}
              </p>
            )}
          </div>
        )}

        {cancelOrder.error !== null && (
          <ErrorBanner error={cancelOrder.error} onDismiss={cancelOrder.reset} />
        )}

        <AsyncBoundary
          state={orders}
          isEmpty={(list) => list.length === 0}
          emptyMessage="주문이 없습니다."
        >
          {(list) => (
            <table className="table">
              <thead>
                <tr>
                  <th>주문번호</th>
                  <th>고객</th>
                  <th>항목</th>
                  <th className="num">총액</th>
                  <th>상태</th>
                  <th>주문 시각</th>
                  <th>취소 시각</th>
                  <th>작업</th>
                </tr>
              </thead>
              <tbody>
                {list.map((order) => (
                  <tr
                    key={order.orderId}
                    className={detailId === order.orderId ? 'is-selected' : undefined}
                  >
                    {/* data-label 은 560px 미만에서 컬럼 헤드를 대신한다 */}
                    <td data-label="주문번호">#{order.orderId}</td>
                    <td className="strong" data-label="고객">
                      {order.customerName}
                    </td>
                    <td className="muted" data-label="항목">
                      {order.items
                        .map((item) => `${item.productName} × ${item.quantity}`)
                        .join(', ')}
                    </td>
                    <td className="num" data-label="총액">
                      {formatKrw(order.totalPrice)}
                    </td>
                    <td data-label="상태">
                      <span
                        className={
                          order.status === 'ORDERED'
                            ? 'badge badge--ok'
                            : 'badge badge--canceled'
                        }
                      >
                        {order.status}
                      </span>
                    </td>
                    <td className="muted" data-label="주문 시각">
                      {formatDateTime(order.orderedAt)}
                    </td>
                    <td className="muted" data-label="취소 시각">
                      {order.canceledAt === null ? '—' : formatDateTime(order.canceledAt)}
                    </td>
                    <td className="actions">
                      <button
                        type="button"
                        className="btn btn--sm"
                        onClick={() => setDetailId(order.orderId)}
                      >
                        상세
                      </button>
                      <button
                        type="button"
                        className="btn btn--sm btn--danger"
                        disabled={cancelOrder.pending}
                        onClick={() => void handleCancel(order)}
                      >
                        주문 취소
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </AsyncBoundary>
        <p className="note">
          취소 버튼은 이미 취소된 주문에도 노출됩니다. 이중 취소를 막는 주체는 화면이 아니라
          서버이며, 그 거부(400 ALREADY_CANCELED)가 실제로 동작하는지 확인할 수 있어야
          합니다.
        </p>
      </div>

      {/*
        차별화 서사 — 유일한 다크 표면(navy). 페이지에서 한 번만 쓴다.
        재시도 버튼이 CONCURRENT_UPDATE 에만 뜨는 이유를 화면이 직접 설명한다.
      */}
      <div className="block block--navy">
        <p className="eyebrow">Concurrency</p>
        <h3 className="headline">재고와 포인트는 낙관적 잠금이 지킨다</h3>
        <p className="subhead">
          같은 상품을 동시에 주문하면 <code>@Version</code> 이 충돌을 잡아내고 서버가 409{' '}
          <code>CONCURRENT_UPDATE</code> 를 돌려준다. 이 코드에만 재시도 버튼이 뜬다. 재고가
          실제로 모자란 400 <code>OUT_OF_STOCK</code> 은 몇 번을 눌러도 결과가 같아서 버튼을
          주지 않는다.
        </p>
      </div>
    </section>
  );
}
