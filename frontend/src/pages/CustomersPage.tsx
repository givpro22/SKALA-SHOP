import { useState, type FormEvent } from 'react';
import { AsyncBoundary } from '../components/AsyncBoundary';
import { ErrorBanner } from '../components/ErrorBanner';
import {
  useChargePoint,
  useCreateCustomer,
  useCustomer,
  useCustomers,
  useDeleteCustomer,
  useUpdateCustomer,
} from '../hooks/useCustomers';
import { formatDateTime, formatDateTimeShort, formatKrw } from '../lib/format';
import type { CustomerResponse } from '../types/api';

interface CustomerForm {
  name: string;
  email: string;
  point: string;
}

const EMPTY_FORM: CustomerForm = { name: '', email: '', point: '' };

export function CustomersPage() {
  const customers = useCustomers();
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useCustomer(detailId);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<CustomerForm>(EMPTY_FORM);

  const [chargeTarget, setChargeTarget] = useState<CustomerResponse | null>(null);
  const [chargeAmount, setChargeAmount] = useState<string>('');

  const createCustomer = useCreateCustomer();
  const updateCustomer = useUpdateCustomer();
  const deleteCustomer = useDeleteCustomer();
  const chargePoint = useChargePoint();

  function resetForm() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    createCustomer.reset();
    updateCustomer.reset();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (editingId !== null) {
      // 계약 §2.4 — 수정 요청에는 point 필드가 없다. 보내도 무시되므로 아예 넣지 않는다.
      const outcome = await updateCustomer.run(editingId, {
        name: form.name,
        email: form.email,
      });
      if (outcome.ok) {
        resetForm();
        customers.reload();
        if (detailId !== null) detail.reload();
      }
      return;
    }

    const trimmedPoint = form.point.trim();
    const point = trimmedPoint === '' ? undefined : Number.parseInt(trimmedPoint, 10);
    if (point !== undefined && Number.isNaN(point)) return;

    const outcome = await createCustomer.run({
      name: form.name,
      email: form.email,
      // 생략하면 서버가 0 으로 채운다(계약 §2.3).
      point,
    });
    if (outcome.ok) {
      resetForm();
      customers.reload();
    }
  }

  async function handleDelete(customer: CustomerResponse) {
    deleteCustomer.reset();
    const outcome = await deleteCustomer.run(customer.customerId);
    if (outcome.ok) {
      if (detailId === customer.customerId) setDetailId(null);
      if (editingId === customer.customerId) resetForm();
      customers.reload();
    }
  }

  async function handleCharge(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (chargeTarget === null) return;

    const amount = Number.parseInt(chargeAmount, 10);
    if (Number.isNaN(amount)) return;

    const outcome = await chargePoint.run(chargeTarget.customerId, { amount });
    if (outcome.ok) {
      setChargeAmount('');
      customers.reload();
      if (detailId !== null) detail.reload();
    }
  }

  const saveError = editingId === null ? createCustomer.error : updateCustomer.error;
  const saving = createCustomer.pending || updateCustomer.pending;

  return (
    <section className="page">
      <header className="page__head">
        <div className="page__title">
          <p className="eyebrow">Customers</p>
          <h2>고객</h2>
        </div>
        <button type="button" className="btn" onClick={customers.reload}>
          새로고침
        </button>
      </header>

      <div className="layout">
        <div className="layout__main">
          <h3>고객 목록</h3>
          <AsyncBoundary
            state={customers}
            isEmpty={(list) => list.length === 0}
            emptyMessage="등록된 고객이 없습니다."
          >
            {(list) => (
              <table className="table">
                <thead>
                  <tr>
                    <th>id</th>
                    <th>이름</th>
                    <th>이메일</th>
                    <th className="num">보유 포인트</th>
                    <th>등록 시각</th>
                    <th>작업</th>
                  </tr>
                </thead>
                <tbody>
                  {list.map((customer) => (
                    <tr
                      key={customer.customerId}
                      className={detailId === customer.customerId ? 'is-selected' : undefined}
                    >
                      {/* data-label 은 560px 미만에서 컬럼 헤드를 대신한다 */}
                      <td data-label="id">{customer.customerId}</td>
                      <td className="strong" data-label="이름">
                        {customer.name}
                      </td>
                      <td className="muted" data-label="이메일">
                        {customer.email}
                      </td>
                      <td className="num" data-label="보유 포인트">
                        <span className="badge badge--point">{formatKrw(customer.point)}</span>
                      </td>
                      <td className="muted" data-label="등록 시각">
                        {formatDateTimeShort(customer.createdAt)}
                      </td>
                      <td className="actions">
                        <button
                          type="button"
                          className="btn btn--sm"
                          onClick={() => setDetailId(customer.customerId)}
                        >
                          상세
                        </button>
                        <button
                          type="button"
                          className="btn btn--sm"
                          onClick={() => {
                            setEditingId(customer.customerId);
                            setForm({
                              name: customer.name,
                              email: customer.email,
                              point: String(customer.point),
                            });
                            createCustomer.reset();
                            updateCustomer.reset();
                          }}
                        >
                          수정
                        </button>
                        <button
                          type="button"
                          className="btn btn--sm"
                          onClick={() => {
                            setChargeTarget(customer);
                            chargePoint.reset();
                          }}
                        >
                          포인트 충전
                        </button>
                        <button
                          type="button"
                          className="btn btn--sm btn--danger"
                          disabled={deleteCustomer.pending}
                          onClick={() => void handleDelete(customer)}
                        >
                          삭제
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </AsyncBoundary>

        </div>

        <aside className="layout__side">
          {/*
           * 삭제 실패 배너는 목록 아래가 아니라 사이드 최상단에 둔다.
           *
           * 목록 아래에 두면 카드·행이 여러 개일 때 배너가 접힌 부분 밑으로 밀려, 삭제를
           * 눌렀는데 화면에 아무 반응이 없는 것처럼 보인다. 주문 화면에서 같은 결함을
           * 라운드 7에 고쳤고(에러 배너를 사이드로), 여기도 같은 규칙을 적용한다.
           */}
          {deleteCustomer.error !== null && (
            <ErrorBanner error={deleteCustomer.error} onDismiss={deleteCustomer.reset} />
          )}
          {/* 입력이 결과를 만드는 곳 — lilac 컬러블록 */}
          <div className="block block--form">
            <h3>{editingId === null ? '고객 등록' : `고객 수정 (id: ${editingId})`}</h3>
            <form className="form" onSubmit={(event) => void handleSubmit(event)}>
              <label>
                이름
                <input
                  type="text"
                  required
                  maxLength={50}
                  value={form.name}
                  onChange={(event) => setForm({ ...form, name: event.target.value })}
                />
              </label>
              <label>
                이메일
                <input
                  type="email"
                  required
                  maxLength={100}
                  value={form.email}
                  onChange={(event) => setForm({ ...form, email: event.target.value })}
                />
              </label>
              <label>
                <span>
                  초기 포인트 <span className="muted">(선택, 생략하면 0)</span>
                </span>
                <input
                  type="number"
                  min={0}
                  value={form.point}
                  disabled={editingId !== null}
                  onChange={(event) => setForm({ ...form, point: event.target.value })}
                />
              </label>
              {editingId !== null && (
                <p className="note">
                  포인트는 수정할 수 없습니다. 변경 경로는 충전과 주문/취소뿐입니다 (계약 §2.4).
                </p>
              )}
              <div className="form__actions">
                <button type="submit" className="btn btn--primary" disabled={saving}>
                  {saving ? '저장 중…' : editingId === null ? '등록' : '수정'}
                </button>
                {editingId !== null && (
                  <button type="button" className="btn" onClick={resetForm}>
                    취소
                  </button>
                )}
              </div>
            </form>

            {saveError !== null && <ErrorBanner error={saveError} />}
          </div>

          {chargeTarget !== null && (
            <div className="card">
              <div className="card__head">
                <h3>포인트 충전</h3>
                <button
                  type="button"
                  className="btn btn--sm"
                  onClick={() => {
                    setChargeTarget(null);
                    chargePoint.reset();
                  }}
                >
                  닫기
                </button>
              </div>
              <p className="muted">
                {chargeTarget.name} (id: {chargeTarget.customerId})
              </p>
              <form className="form" onSubmit={(event) => void handleCharge(event)}>
                <label>
                  충전 금액 (원)
                  <input
                    type="number"
                    required
                    min={1}
                    value={chargeAmount}
                    onChange={(event) => setChargeAmount(event.target.value)}
                  />
                </label>
                <p className="note">
                  충전은 멱등하지 않습니다. 두 번 누르면 두 번 충전됩니다.
                </p>
                <button type="submit" className="btn btn--primary" disabled={chargePoint.pending}>
                  {chargePoint.pending ? '충전 중…' : '충전'}
                </button>
              </form>

              {chargePoint.result !== null && (
                <div className="banner banner--success block block--ok" role="status">
                  <strong className="banner__title">충전 완료</strong>
                  <p>
                    {chargePoint.result.name} 님의 보유 포인트가{' '}
                    <strong>{formatKrw(chargePoint.result.point)}</strong> 이 되었습니다.
                  </p>
                </div>
              )}
              {chargePoint.error !== null && <ErrorBanner error={chargePoint.error} />}
            </div>
          )}

          {detailId !== null && (
            <div className="card">
              <div className="card__head">
                <h3>고객 상세</h3>
                <button type="button" className="btn btn--sm" onClick={() => setDetailId(null)}>
                  닫기
                </button>
              </div>
              <AsyncBoundary state={detail} emptyMessage="선택된 고객이 없습니다.">
                {(customer) =>
                  customer === null ? (
                    <p className="state state--empty">선택된 고객이 없습니다.</p>
                  ) : (
                    <dl className="detail">
                      <dt>customerId</dt>
                      <dd>{customer.customerId}</dd>
                      <dt>이름</dt>
                      <dd className="strong">{customer.name}</dd>
                      <dt>이메일</dt>
                      <dd>{customer.email}</dd>
                      <dt>보유 포인트</dt>
                      <dd>
                        <span className="badge badge--point">{formatKrw(customer.point)}</span>
                      </dd>
                      <dt>등록 시각</dt>
                      <dd className="muted">{formatDateTime(customer.createdAt)}</dd>
                      <dt>수정 시각</dt>
                      <dd className="muted">{formatDateTime(customer.updatedAt)}</dd>
                    </dl>
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
