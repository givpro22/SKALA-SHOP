import { useState, type FormEvent } from 'react';
import { AsyncBoundary } from '../components/AsyncBoundary';
import { ErrorBanner } from '../components/ErrorBanner';
import { ProductThumb } from '../components/ProductThumb';
import type { CartState } from '../hooks/useCart';
import { useProduct } from '../hooks/useProducts';
import { useShopProducts } from '../hooks/useShopProducts';
import { formatKrw } from '../lib/format';
import type { ProductSort } from '../types/api';

const PAGE_SIZE = 12;

const SORT_OPTIONS: { value: ProductSort; label: string }[] = [
  { value: 'LATEST', label: '최신순' },
  { value: 'PRICE_ASC', label: '낮은 가격순' },
  { value: 'PRICE_DESC', label: '높은 가격순' },
];

/**
 * `select.value` 는 항상 `string` 이다. `as ProductSort` 로 덮지 않고 **실제로 확인한다.**
 *
 * 캐스팅은 "내가 옵션을 셋으로 고정해 뒀으니 괜찮다"는 약속인데, 나중에 옵션이 하나 늘거나
 * 값이 오타로 바뀌면 그 약속만 조용히 깨지고 서버가 400 `VALIDATION_ERROR` 를 낸다.
 * 목록에서 직접 확인하면 옵션과 검증이 **같은 배열 하나**에서 나온다.
 */
function isProductSort(value: string): value is ProductSort {
  return SORT_OPTIONS.some((option) => option.value === value);
}

interface Props {
  cart: CartState;
  /** `SHOPPER` 로 로그인했는가. 아니면 담기 버튼 대신 이유를 보여준다. */
  canBuy: boolean;
  onCartChanged: () => void;
}

/**
 * 스토어프론트 상품 목록 — `GET /api/shop/products`.
 *
 * <p>관리자 상품 화면(`ProductsPage`)과 **같은 데이터를 다른 질문으로 본다.** 관리자는
 * "무엇을 고쳐야 하는가"라 등록 시각·수정 시각·id 가 필요하고, 구매자는 "무엇을 살까"라
 * 검색·정렬·가격·재고만 필요하다. 한 화면에 겹치면 양쪽 다 군더더기가 된다.
 *
 * <p>**검색은 입력할 때마다가 아니라 제출할 때 나간다.** 타이핑마다 요청하면 "키보드"를
 * 치는 동안 4번이 나가고, 늦게 도착한 "키보"의 응답이 "키보드"의 결과를 덮을 수 있다.
 * 디바운스로 줄일 수는 있어도 없앨 수는 없다 — 제출 시점이 하나면 그 경합 자체가 없다.
 */
export function StorefrontPage({ cart, canBuy, onCartChanged }: Props) {
  // 입력 중인 값과 실제로 요청에 실린 값을 분리한다. 하나로 두면 타이핑이 곧 요청이 된다.
  const [keyword, setKeyword] = useState('');
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<ProductSort>('LATEST');
  const [page, setPage] = useState(0);

  const products = useShopProducts({ q, sort, page, size: PAGE_SIZE });
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useProduct(detailId);
  const [quantity, setQuantity] = useState('1');
  const [added, setAdded] = useState<string | null>(null);

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setQ(keyword);
    // 검색어를 바꾸면 1페이지로 돌아간다. 3페이지에서 검색하면 결과가 1페이지뿐일 때
    // 빈 화면이 뜨고, 사용자는 "검색 결과가 없다"로 읽는다.
    setPage(0);
  }

  function resetSearch() {
    setKeyword('');
    setQ('');
    setPage(0);
  }

  async function addToCart(productId: number, name: string, amount: number) {
    setAdded(null);
    const ok = await cart.addItem(productId, amount);
    if (ok) {
      setAdded(`${name} ${amount}개를 장바구니에 담았습니다.`);
      onCartChanged();
    }
  }

  return (
    <section className="page">
      <header className="page__head">
        <div className="page__title">
          <p className="eyebrow">Storefront</p>
          <h2>상품 둘러보기</h2>
        </div>
        <button type="button" className="btn" onClick={products.reload}>
          새로고침
        </button>
      </header>

      <form className="form form--inline" onSubmit={handleSearch} role="search">
        <label className="inline-field">
          검색
          <input
            type="search"
            name="q"
            maxLength={100}
            placeholder="상품명으로 검색 (예: 키보드)"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
        </label>
        <label className="inline-field">
          정렬
          <select
            name="sort"
            value={sort}
            onChange={(event) => {
              // 정렬 값은 계약이 열거한 셋뿐이다(LATEST·PRICE_ASC·PRICE_DESC).
              if (!isProductSort(event.target.value)) return;
              setSort(event.target.value);
              // 정렬이 바뀌면 1페이지로 돌아간다. 3페이지에서 정렬만 바꾸면 사용자가 보고
              // 있던 것과 전혀 다른 구간이 같은 페이지 번호로 나온다.
              setPage(0);
            }}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <div className="form__actions">
          {/*
            프라이머리가 아니다. 이 페이지의 결정은 "담는 것"이고 검색은 목록을 좁히는
            도구다. 검정 필을 여기에 두면 그 무게가 결정에서 필터로 옮겨간다.
          */}
          <button type="submit" className="btn">
            검색
          </button>
          {q !== '' && (
            <button type="button" className="btn" onClick={resetSearch}>
              전체 보기
            </button>
          )}
        </div>
      </form>

      <div className="layout">
        <div className="layout__main">
          <AsyncBoundary
            state={products}
            isEmpty={(pageData) => pageData.items.length === 0}
            emptyMessage={
              q === ''
                ? '등록된 상품이 없습니다.'
                : `‘${q}’에 해당하는 상품이 없습니다. 다른 검색어를 입력해 보세요.`
            }
          >
            {(pageData) => (
              <>
                <p className="listmeta" data-total-elements={pageData.totalElements}>
                  전체 <strong>{pageData.totalElements}</strong>건 · {pageData.page + 1} /{' '}
                  {pageData.totalPages}페이지
                  {q !== '' && <> · 검색어 ‘{q}’</>}
                </p>

                {/*
                  `pageData.items` 를 읽는 이 한 줄이 이 프로젝트의 유일한 unwrap 지점이다.
                  다른 목록은 응답이 곧 배열이라 `.items` 가 없다.
                */}
                <div className="pgrid">
                  {pageData.items.map((product) => (
                    <article
                      key={product.productId}
                      className={detailId === product.productId ? 'pcard is-selected' : 'pcard'}
                    >
                      <ProductThumb product={product} />
                      <div className="pcard__body">
                        <h4 className="pcard__name">{product.name}</h4>
                        <p className="pcard__desc">{product.description ?? '—'}</p>
                        <p className="pcard__meta">
                          <span className="pcard__price">{formatKrw(product.price)}</span>
                          <span
                            className={product.stock <= 2 ? 'badge badge--warn' : 'pcard__stock'}
                          >
                            {product.stock === 0 ? '품절' : `재고 ${product.stock}`}
                          </span>
                        </p>
                      </div>
                      <div className="pcard__actions">
                        <button
                          type="button"
                          className="btn btn--sm"
                          onClick={() => {
                            setDetailId(product.productId);
                            setQuantity('1');
                            setAdded(null);
                            cart.dismissMutationError();
                          }}
                        >
                          상세
                        </button>
                        {canBuy && (
                          /*
                           * 재고 0이어도 버튼을 막지 않는다. 화면의 재고는 마지막 조회 시점
                           * 값이라 서버와 시차가 있고, 프론트가 판정하면 멀쩡한 주문을 막거나
                           * 이미 불가능한 담기를 통과시킨다. 판정은 서버가 하고 화면은 그
                           * 결과(400 OUT_OF_STOCK)를 보여준다.
                           */
                          <button
                            type="button"
                            className="btn btn--sm btn--outline"
                            disabled={cart.pending}
                            onClick={() =>
                              void addToCart(product.productId, product.name, 1)
                            }
                          >
                            담기
                          </button>
                        )}
                      </div>
                    </article>
                  ))}
                </div>

                <nav className="actions actions--pager" aria-label="페이지 이동">
                  <button
                    type="button"
                    className="btn btn--sm"
                    disabled={pageData.page === 0}
                    onClick={() => setPage(pageData.page - 1)}
                  >
                    이전
                  </button>
                  <span className="muted">
                    {pageData.page + 1} / {pageData.totalPages}
                  </span>
                  {/* hasNext 를 서버가 계산해 준다. totalPages 와 page 로 프론트가 다시
                      계산하면 경계 조건(0건일 때 totalPages 가 0)에서 어긋난다. */}
                  <button
                    type="button"
                    className="btn btn--sm"
                    disabled={!pageData.hasNext}
                    onClick={() => setPage(pageData.page + 1)}
                  >
                    다음
                  </button>
                </nav>
              </>
            )}
          </AsyncBoundary>
        </div>

        <aside className="layout__side">
          {cart.mutationError !== null && (
            <ErrorBanner error={cart.mutationError} onDismiss={cart.dismissMutationError} />
          )}

          {added !== null && (
            <p className="state block block--ok" role="status">
              {added}
            </p>
          )}

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
                    <>
                      <ProductThumb product={product} />
                      <dl className="detail">
                        <dt>상품명</dt>
                        <dd className="strong">{product.name}</dd>
                        <dt>설명</dt>
                        <dd>{product.description ?? '—'}</dd>
                        <dt>판매가</dt>
                        <dd className="num">{formatKrw(product.price)}</dd>
                        <dt>재고</dt>
                        <dd>{product.stock === 0 ? '품절' : `${product.stock}개`}</dd>
                      </dl>

                      {canBuy ? (
                        <div className="form__actions">
                          <label className="inline-field">
                            수량
                            <input
                              type="number"
                              min={1}
                              max={99}
                              value={quantity}
                              onChange={(event) => setQuantity(event.target.value)}
                            />
                          </label>
                          <button
                            type="button"
                            className="btn btn--primary"
                            disabled={cart.pending}
                            onClick={() => {
                              const amount = Number.parseInt(quantity, 10);
                              if (Number.isNaN(amount)) return;
                              void addToCart(product.productId, product.name, amount);
                            }}
                          >
                            {cart.pending ? '담는 중…' : '장바구니에 담기'}
                          </button>
                        </div>
                      ) : (
                        <p className="note">
                          장바구니에 담으려면 구매자 계정으로 로그인해 주세요.
                        </p>
                      )}
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
