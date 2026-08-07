import { useState } from 'react';
import { AuthBar } from './components/AuthBar';
import { useAuth } from './hooks/useAuth';
import { CustomersPage } from './pages/CustomersPage';
import { OrdersPage } from './pages/OrdersPage';
import { ProductsPage } from './pages/ProductsPage';

type Tab = 'products' | 'customers' | 'orders';

const TABS: { id: Tab; label: string }[] = [
  { id: 'products', label: '상품' },
  { id: 'customers', label: '고객' },
  { id: 'orders', label: '주문' },
];

// 라우터를 쓰지 않는다. 화면이 셋뿐이고 URL 공유 요구도 없어서, 의존성을 하나 늘리는 것보다
// 상태 하나로 두는 편이 얻는 것이 많다.
export default function App() {
  const [tab, setTab] = useState<Tab>('products');
  const auth = useAuth();

  return (
    <div className="app">
      <header className="app__head">
        <h1>SKALA-SHOP</h1>
        <nav className="tabs">
          {TABS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={item.id === tab ? 'tab is-active' : 'tab'}
              onClick={() => setTab(item.id)}
            >
              {item.label}
            </button>
          ))}
        </nav>
        <span className="app__api">API {import.meta.env.VITE_API_BASE_URL ?? '(same-origin)'}</span>
        <AuthBar auth={auth} />
      </header>

      <main className="app__body">
        {tab === 'products' && <ProductsPage />}
        {tab === 'customers' && <CustomersPage />}
        {tab === 'orders' && <OrdersPage />}
      </main>

      <footer className="app__foot">
        SKALA-SHOP · 상품 · 고객 · 주문
        {!auth.loggedIn && ' · 조회는 로그인 없이, 등록·수정·삭제는 로그인 후'}
      </footer>
    </div>
  );
}
