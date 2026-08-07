import { useState, type FormEvent } from 'react';
import { AsyncBoundary } from '../components/AsyncBoundary';
import { ErrorBanner } from '../components/ErrorBanner';
import {
  useCreateProduct,
  useDeleteProduct,
  useProduct,
  useProducts,
  useUpdateProduct,
} from '../hooks/useProducts';
import { formatDateTime, formatKrw } from '../lib/format';
import type { ProductResponse } from '../types/api';

interface ProductForm {
  name: string;
  description: string;
  price: string;
  stock: string;
}

const EMPTY_FORM: ProductForm = { name: '', description: '', price: '', stock: '' };

function toForm(product: ProductResponse): ProductForm {
  return {
    name: product.name,
    // description 은 null 일 수 있다. input 의 value 에 null 을 넣으면 React 가 경고한다.
    description: product.description ?? '',
    price: String(product.price),
    stock: String(product.stock),
  };
}

export function ProductsPage() {
  const products = useProducts();
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useProduct(detailId);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<ProductForm>(EMPTY_FORM);

  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct();
  const deleteProduct = useDeleteProduct();

  function resetForm() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    createProduct.reset();
    updateProduct.reset();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const price = Number.parseInt(form.price, 10);
    const stock = Number.parseInt(form.stock, 10);
    if (Number.isNaN(price) || Number.isNaN(stock)) return;

    const body = {
      name: form.name,
      // 빈 문자열은 "설명 없음" 이다. PUT 은 전체 교체이므로 null 로 명시해 덮어쓴다.
      description: form.description.trim() === '' ? null : form.description,
      price,
      stock,
    };

    const outcome =
      editingId === null
        ? await createProduct.run(body)
        : await updateProduct.run(editingId, body);

    if (outcome.ok) {
      resetForm();
      products.reload();
      if (detailId !== null) detail.reload();
    }
  }

  async function handleDelete(product: ProductResponse) {
    deleteProduct.reset();
    // DELETE 는 204 라 성공값이 없다. outcome.ok 로 판정한다.
    const outcome = await deleteProduct.run(product.productId);
    if (outcome.ok) {
      if (detailId === product.productId) setDetailId(null);
      if (editingId === product.productId) resetForm();
      products.reload();
    }
  }

  const saveError = editingId === null ? createProduct.error : updateProduct.error;
  const saving = createProduct.pending || updateProduct.pending;

  return (
    <section className="page">
      <header className="page__head">
        <div className="page__title">
          <p className="eyebrow">Products</p>
          <h2>상품</h2>
        </div>
        <button type="button" className="btn" onClick={products.reload}>
          새로고침
        </button>
      </header>

      <div className="layout">
        <div className="layout__main">
          <h3>상품 목록</h3>
          <AsyncBoundary
            state={products}
            isEmpty={(list) => list.length === 0}
            // 1단으로 접히면 폼이 목록 아래로 내려가므로 방향("오른쪽")을 쓰지 않는다.
            emptyMessage="등록된 상품이 없습니다. 등록 폼으로 첫 상품을 추가해 보세요."
          >
            {(list) => (
              <table className="table">
                <thead>
                  <tr>
                    <th>id</th>
                    <th>상품명</th>
                    <th>설명</th>
                    <th className="num">단가</th>
                    <th className="num">재고</th>
                    <th>수정 시각</th>
                    <th>작업</th>
                  </tr>
                </thead>
                <tbody>
                  {list.map((product) => (
                    <tr
                      key={product.productId}
                      className={detailId === product.productId ? 'is-selected' : undefined}
                    >
                      {/* data-label 은 560px 미만에서 컬럼 헤드를 대신한다. thead 가 사라지고
                          각 값 앞에 mono 캡션으로 다시 나타난다. */}
                      <td data-label="id">{product.productId}</td>
                      <td className="strong" data-label="상품명">
                        {product.name}
                      </td>
                      <td className="muted" data-label="설명">
                        {product.description ?? '—'}
                      </td>
                      <td className="num" data-label="단가">
                        {formatKrw(product.price)}
                      </td>
                      <td className="num" data-label="재고">
                        <span className={product.stock <= 2 ? 'badge badge--warn' : undefined}>
                          {product.stock}
                        </span>
                      </td>
                      <td className="muted" data-label="수정 시각">
                        {formatDateTime(product.updatedAt)}
                      </td>
                      <td className="actions">
                        <button
                          type="button"
                          className="btn btn--sm"
                          onClick={() => setDetailId(product.productId)}
                        >
                          상세
                        </button>
                        <button
                          type="button"
                          className="btn btn--sm"
                          onClick={() => {
                            setEditingId(product.productId);
                            setForm(toForm(product));
                            createProduct.reset();
                            updateProduct.reset();
                          }}
                        >
                          수정
                        </button>
                        <button
                          type="button"
                          className="btn btn--sm btn--danger"
                          disabled={deleteProduct.pending}
                          onClick={() => void handleDelete(product)}
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

          {deleteProduct.error !== null && (
            <ErrorBanner error={deleteProduct.error} onDismiss={deleteProduct.reset} />
          )}
        </div>

        <aside className="layout__side">
          {/* 입력이 결과를 만드는 곳 — lilac 컬러블록 */}
          <div className="block block--form">
            <h3>{editingId === null ? '상품 등록' : `상품 수정 (id: ${editingId})`}</h3>
            <form className="form" onSubmit={(event) => void handleSubmit(event)}>
              <label>
                상품명
                <input
                  type="text"
                  required
                  maxLength={100}
                  value={form.name}
                  onChange={(event) => setForm({ ...form, name: event.target.value })}
                />
              </label>
              <label>
                <span>
                  설명 <span className="muted">(선택)</span>
                </span>
                <input
                  type="text"
                  maxLength={500}
                  value={form.description}
                  onChange={(event) => setForm({ ...form, description: event.target.value })}
                />
              </label>
              <label>
                단가 (원)
                <input
                  type="number"
                  required
                  min={0}
                  value={form.price}
                  onChange={(event) => setForm({ ...form, price: event.target.value })}
                />
              </label>
              <label>
                재고
                <input
                  type="number"
                  required
                  min={0}
                  value={form.stock}
                  onChange={(event) => setForm({ ...form, stock: event.target.value })}
                />
              </label>
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

          {detailId !== null && (
            <div className="card">
              <div className="card__head">
                <h3>상품 상세</h3>
                <button type="button" className="btn btn--sm" onClick={() => setDetailId(null)}>
                  닫기
                </button>
              </div>
              <AsyncBoundary state={detail} emptyMessage="선택된 상품이 없습니다.">
                {(product) =>
                  product === null ? (
                    <p className="state state--empty">선택된 상품이 없습니다.</p>
                  ) : (
                    <dl className="detail">
                      <dt>productId</dt>
                      <dd>{product.productId}</dd>
                      <dt>상품명</dt>
                      <dd className="strong">{product.name}</dd>
                      <dt>설명</dt>
                      <dd>{product.description ?? '—'}</dd>
                      <dt>단가</dt>
                      <dd>{formatKrw(product.price)}</dd>
                      <dt>재고</dt>
                      <dd>{product.stock}개</dd>
                      <dt>등록 시각</dt>
                      <dd className="muted">{formatDateTime(product.createdAt)}</dd>
                      <dt>수정 시각</dt>
                      <dd className="muted">{formatDateTime(product.updatedAt)}</dd>
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
