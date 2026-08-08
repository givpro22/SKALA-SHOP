import { useState } from 'react';
import { AuthBar } from './components/AuthBar';
import { ShopperGate } from './components/ShopperGate';
import { useAuth } from './hooks/useAuth';
import { useCart } from './hooks/useCart';
import { toRoleView, useShopper } from './hooks/useShopper';
import { CartPage } from './pages/CartPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { CustomersPage } from './pages/CustomersPage';
import { MyOrdersPage } from './pages/MyOrdersPage';
import { OrdersPage } from './pages/OrdersPage';
import { ProductsPage } from './pages/ProductsPage';
import { StorefrontPage } from './pages/StorefrontPage';

/**
 * 화면은 두 **영역**으로 갈린다 — 구매자(store)와 관리자(admin).
 *
 * <p>계약 §9.0 이 경로를 `/api/shop/**` 와 `/api/**` 로 나눈 것과 같은 경계다. 서버에서
 * 갈라 둔 것을 화면에서 한 덩어리로 보여주면, 사용자는 왜 어떤 버튼은 되고 어떤 버튼은
 * 403 이 나는지 알 수 없다. **경계가 서버에만 있고 화면에 없으면 그건 숨겨진 규칙이다.**
 */
type Area = 'store' | 'admin';
type StoreTab = 'browse' | 'cart' | 'checkout' | 'orders';
type AdminTab = 'products' | 'customers' | 'orders';

const STORE_TABS: { id: StoreTab; label: string; shopperOnly: boolean }[] = [
  { id: 'browse', label: '상품 둘러보기', shopperOnly: false },
  { id: 'cart', label: '장바구니', shopperOnly: true },
  { id: 'checkout', label: '주문서', shopperOnly: true },
  { id: 'orders', label: '내 주문', shopperOnly: true },
];

const ADMIN_TABS: { id: AdminTab; label: string }[] = [
  { id: 'products', label: '상품 관리' },
  { id: 'customers', label: '고객' },
  { id: 'orders', label: '주문' },
];

// 라우터를 쓰지 않는다. URL 공유 요구가 없고, 의존성 하나를 늘리는 것보다 상태 둘로
// 두는 편이 얻는 것이 많다. 영역이 늘거나 딥링크가 필요해지면 그때 바꾼다.
export default function App() {
  const [area, setArea] = useState<Area>('store');
  const [storeTab, setStoreTab] = useState<StoreTab>('browse');
  const [adminTab, setAdminTab] = useState<AdminTab>('products');

  const auth = useAuth();
  const shopper = useShopper(auth.loggedIn);
  const role = toRoleView(shopper.data ?? null);

  // 카트는 SHOPPER 만 부를 수 있다. ADMIN 이 부르면 403, 비로그인이면 401 이며 둘 다
  // 정상 상태다 — 그래서 요청 자체를 내지 않는다(§9.4.2).
  const cart = useCart(auth.loggedIn && role.isShopper);

  const cartCount = shopper.data?.cartItemCount ?? 0;

  /**
   * 카트나 주문이 바뀌면 헤더의 배지·잔액을 다시 읽는다.
   *
   * <p>헤더가 읽는 값(`cartItemCount`·`point`)의 출처는 카트가 아니라 `/api/shop/me` 다.
   * 그래서 **카트를 바꾸는 모든 경로가 이걸 함께 불러야 한다** — 담기·수량·삭제·비우기는
   * 페이지가, 체크아웃은 `useCheckout` 이 부른다(성공을 아는 곳이 그 훅뿐이다).
   */
  function refreshShopper() {
    shopper.reload();
  }

  const visibleStoreTabs = STORE_TABS.filter((tab) => !(tab.shopperOnly && role.isAdmin));

  return (
    <div className="app">
      <header className="app__head">
        <h1 className="brand">
          <span className="brand__mark">
            <img src="/skala-logo.png" alt="SKALA" width={228} height={56} />
          </span>
          SHOP
        </h1>

        <nav className="tabs" aria-label="영역">
          <button
            type="button"
            className={area === 'store' ? 'tab is-active' : 'tab'}
            onClick={() => setArea('store')}
            data-area="store"
          >
            스토어
          </button>
          <button
            type="button"
            className={area === 'admin' ? 'tab is-active' : 'tab'}
            onClick={() => setArea('admin')}
            data-area="admin"
          >
            관리
            {/*
              ADMIN 이 아닌 사용자에게 관리 영역을 **감추지 않는다.** 조회는 계약상 공개이고
              (§9.4.2 `GET /api/**` 인증 불필요), 감추면 "왜 안 보이지"가 되며 무엇이
              관리자 전용인지도 알 수 없다. 대신 자물쇠로 **읽기 전용임을 미리 알린다.**
            */}
            {!role.isAdmin && (
              <span className="badge" title="조회만 가능합니다">
                읽기 전용
              </span>
            )}
          </button>
        </nav>

        <span className="app__api">API {import.meta.env.VITE_API_BASE_URL ?? '(same-origin)'}</span>

        {/* 역할과 카트 배지. 로그인 상태만으로는 무엇을 할 수 있는지 알 수 없다. */}
        {shopper.data !== null && (
          <span className="authbar__who" data-role={shopper.data.role}>
            {shopper.data.role}
            {shopper.data.role === 'SHOPPER' && (
              <>
                {' · 장바구니 '}
                <strong data-cart-count={cartCount}>{cartCount}</strong>
                {shopper.data.point !== null && (
                  <> · {shopper.data.point.toLocaleString('ko-KR')}P</>
                )}
              </>
            )}
          </span>
        )}

        <AuthBar auth={auth} />
      </header>

      <nav className="tabs" aria-label="화면">
        {area === 'store'
          ? visibleStoreTabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                className={tab.id === storeTab ? 'tab is-active' : 'tab'}
                onClick={() => setStoreTab(tab.id)}
              >
                {tab.label}
                {tab.id === 'cart' && cartCount > 0 && <> ({cartCount})</>}
              </button>
            ))
          : ADMIN_TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                className={tab.id === adminTab ? 'tab is-active' : 'tab'}
                onClick={() => setAdminTab(tab.id)}
              >
                {tab.label}
              </button>
            ))}
      </nav>

      <main className="app__body">
        {area === 'store' && (
          <>
            {/*
              ADMIN 에게는 구매 탭이 아예 보이지 않는다. 눌러 봐야 403 이고, 그 403 은
              사용자가 고칠 수 있는 것이 아니다 — 계정의 역할이 그렇기 때문이다.
              탭을 숨기는 대신 그 이유를 한 줄로 남긴다.
            */}
            {role.isAdmin && (
              <p className="note note--warn role-notice" data-role-notice="ADMIN">
                관리자 계정으로 로그인되어 있어 장바구니·주문 기능이 표시되지 않습니다. 서버도 이
                계정의 <code>/api/shop/cart</code> 요청을 <code>403 FORBIDDEN</code> 으로
                거부합니다 — 관리 권한과 구매 권한이 겹치지 않게 하기 위한 것입니다.
              </p>
            )}

            {storeTab === 'browse' && (
              <StorefrontPage
                cart={cart}
                canBuy={auth.loggedIn && role.isShopper}
                onCartChanged={refreshShopper}
              />
            )}
            {storeTab === 'cart' && (
              <ShopperGate loggedIn={auth.loggedIn} role={role}>
                <CartPage
                  cart={cart}
                  onCartChanged={refreshShopper}
                  onGoCheckout={() => setStoreTab('checkout')}
                />
              </ShopperGate>
            )}
            {storeTab === 'checkout' && (
              <ShopperGate loggedIn={auth.loggedIn} role={role}>
                <CheckoutPage
                  cart={cart}
                  shopper={shopper.data ?? null}
                  onCheckedOut={refreshShopper}
                  // 아래 둘은 **이동만** 한다. 갱신은 주문이 성공한 순간에 이미 끝났고,
                  // 여기서 또 부르면 같은 요청이 한 번 더 나갈 뿐이다.
                  onOrdered={() => setStoreTab('browse')}
                  onGoOrders={() => setStoreTab('orders')}
                />
              </ShopperGate>
            )}
            {storeTab === 'orders' && (
              <ShopperGate loggedIn={auth.loggedIn} role={role}>
                <MyOrdersPage enabled={role.isShopper} onOrderChanged={refreshShopper} />
              </ShopperGate>
            )}
          </>
        )}

        {area === 'admin' && (
          <>
            {/*
              SHOPPER·비로그인에게도 관리 화면을 보여주되 **버튼을 비활성화하지 않는다.**
              누르면 서버가 403 으로 거부하고 그 배너가 뜬다 — 역할 경계가 실제로 걸려 있다는
              증거는 그 화면뿐이며, 프론트가 미리 막으면 확인할 방법이 사라진다.
            */}
            {!role.isAdmin && (
              <p className="note note--warn role-notice" data-role-notice="NOT_ADMIN">
                {auth.loggedIn
                  ? '이 계정은 관리자가 아닙니다. 조회는 되지만 등록·수정·삭제는 서버가 403 FORBIDDEN 으로 거부합니다.'
                  : '로그인하지 않아도 조회는 가능합니다. 등록·수정·삭제에는 관리자 계정이 필요합니다.'}
              </p>
            )}
            {adminTab === 'products' && <ProductsPage />}
            {adminTab === 'customers' && <CustomersPage />}
            {adminTab === 'orders' && <OrdersPage />}
          </>
        )}
      </main>

      <footer className="app__foot">
        SKALA-SHOP · 스토어(구매) · 관리(상품 · 고객 · 주문)
        {!auth.loggedIn && ' · 조회는 로그인 없이, 구매·등록·수정·삭제는 로그인 후'}
      </footer>
    </div>
  );
}
