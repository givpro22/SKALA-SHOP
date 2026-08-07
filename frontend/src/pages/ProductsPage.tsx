import { useState, type FormEvent } from 'react';
import { AsyncBoundary } from '../components/AsyncBoundary';
import { ErrorBanner } from '../components/ErrorBanner';
import { ProductThumb } from '../components/ProductThumb';
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
  imageUrl: string;
}

const EMPTY_FORM: ProductForm = { name: '', description: '', price: '', stock: '', imageUrl: '' };

function toForm(product: ProductResponse): ProductForm {
  return {
    name: product.name,
    // description 은 null 일 수 있다. input 의 value 에 null 을 넣으면 React 가 경고한다.
    description: product.description ?? '',
    price: String(product.price),
    stock: String(product.stock),
    imageUrl: product.imageUrl ?? '',
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
      // 설명과 같은 이유로 빈 문자열은 null 이다. PUT 이 전체 교체이므로 명시해 덮어쓴다.
      imageUrl: form.imageUrl.trim() === '' ? null : form.imageUrl.trim(),
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
              // 표가 아니라 카드 그리드다. 상품은 그림이 있는 대상이라 행으로 늘어놓으면
              // 썸네일이 셀 하나에 갇혀 아무 역할도 못 한다. 내부 id·수정 시각처럼
              // 사용자가 판단에 쓰지 않는 값은 카드에서 빼고 상세 패널로 넘겼다.
              <div className="pgrid">
                {list.map((product) => (
                  <article
                    key={product.productId}
                    className={
                      detailId === product.productId ? 'pcard is-selected' : 'pcard'
                    }
                  >
                    <ProductThumb product={product} />

                    <div className="pcard__body">
                      <h4 className="pcard__name">{product.name}</h4>
                      <p className="pcard__desc">{product.description ?? '—'}</p>
                      <p className="pcard__meta">
                        <span className="pcard__price">{formatKrw(product.price)}</span>
                        {/* 정상 재고는 배지로 만들지 않는다. 목록의 모든 카드가 검은 필을
                            달면 삭제 버튼을 코랄로 채웠을 때와 같은 문제가 된다 — 강조가
                            반복되면 정작 부족한 재고가 눈에 띄지 않는다. */}
                        <span
                          className={
                            product.stock <= 2 ? 'badge badge--warn' : 'pcard__stock'
                          }
                        >
                          재고 {product.stock}
                        </span>
                      </p>
                    </div>

                    <div className="pcard__actions">
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
                    </div>
                  </article>
                ))}
              </div>
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
          {deleteProduct.error !== null && (
            <ErrorBanner error={deleteProduct.error} onDismiss={deleteProduct.reset} />
          )}
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
              <label>
                이미지 주소 (선택)
                <input
                  type="text"
                  maxLength={500}
                  placeholder="/products/mouse.svg"
                  value={form.imageUrl}
                  onChange={(event) => setForm({ ...form, imageUrl: event.target.value })}
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
