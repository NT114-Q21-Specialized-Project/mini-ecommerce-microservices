import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import {
  ListOrdered,
  Search,
  ShieldCheck,
  ShoppingBag,
  ShoppingCart,
  Store,
  Users2,
} from 'lucide-react';

import Header from './components/Layout/Header';
import Message from './components/Layout/Message';
import AuthForm from './components/Auth/AuthForm';
import UserSidebar from './components/User/UserSidebar';
import ProductList from './components/Product/ProductList';
import ProductForm from './components/Product/ProductForm';
import OrderPanel from './components/Order/OrderPanel';

const GATEWAY_URL = 'http://localhost:9000/api';
const SESSION_STORAGE_KEY = 'mini-ecom-session';
const DEFAULT_ROUTE = '/dashboard';

const navItems = [
  { path: '/dashboard', label: 'Dashboard', icon: ShoppingBag },
  { path: '/products', label: 'Products', icon: Store },
  { path: '/orders', label: 'Orders', icon: ListOrdered, roles: ['CUSTOMER', 'ADMIN'] },
  { path: '/users', label: 'Users', icon: Users2 },
  { path: '/admin', label: 'Admin', icon: ShieldCheck, roles: ['ADMIN'] },
];

function App() {
  const [isLoginView, setIsLoginView] = useState(true);
  const [session, setSession] = useState(null);
  const [users, setUsers] = useState([]);
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [searchTerm, setSearchTerm] = useState('');
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    role: 'CUSTOMER',
  });
  const [isProductModalOpen, setIsProductModalOpen] = useState(false);
  const [route, setRoute] = useState(() => {
    if (!window.location.hash) {
      window.location.hash = `#${DEFAULT_ROUTE}`;
      return DEFAULT_ROUTE;
    }
    return window.location.hash.replace('#', '') || DEFAULT_ROUTE;
  });

  const currentUser = session?.user || null;
  const authToken = session?.accessToken || '';
  const role = currentUser?.role;

  const tokenExpired = useMemo(() => {
    if (!session?.expiresAt) {
      return false;
    }
    return Date.now() >= session.expiresAt * 1000;
  }, [session]);

  useEffect(() => {
    const savedSession = localStorage.getItem(SESSION_STORAGE_KEY);
    if (savedSession) {
      try {
        const parsedSession = JSON.parse(savedSession);
        setSession(parsedSession);
      } catch {
        localStorage.removeItem(SESSION_STORAGE_KEY);
      }
    }
    fetchProducts();
  }, []);

  useEffect(() => {
    if (tokenExpired) {
      handleLogout('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.');
    }
  }, [tokenExpired]);

  useEffect(() => {
    if (!currentUser || !authToken) {
      setUsers([]);
      setOrders([]);
      return;
    }

    if (currentUser.role === 'ADMIN') {
      fetchUsers();
    }

    if (currentUser.role === 'CUSTOMER' || currentUser.role === 'ADMIN') {
      fetchOrders();
    } else {
      setOrders([]);
    }
  }, [currentUser, authToken]);

  const showMsg = (type, text) => {
    setMessage({ type, text });
    setTimeout(() => setMessage({ type: '', text: '' }), 3500);
  };

  const filteredProducts = useMemo(() => {
    if (!searchTerm.trim()) {
      return products;
    }
    const normalized = searchTerm.trim().toLowerCase();
    return products.filter((product) => product.name?.toLowerCase().includes(normalized));
  }, [products, searchTerm]);

  const isRouteAllowed = (item) => !item.roles || (role && item.roles.includes(role));
  const showSearchBar = ['/dashboard', '/products'].includes(route);
  const showOrderSidebar =
    ['/dashboard', '/products', '/users', '/admin'].includes(route) &&
    (role === 'CUSTOMER' || role === 'ADMIN');

  const renderNotAllowed = (title, subtitle) => (
    <div className="glass-panel rounded-[32px] border p-6 text-center">
      <p className="text-xs font-semibold uppercase tracking-[0.25em] text-slate-400">Restricted</p>
      <h3 className="mt-3 text-xl font-bold text-slate-900">{title}</h3>
      <p className="mt-2 text-sm text-slate-500">{subtitle}</p>
    </div>
  );

  const renderUsersView = () => {
    if (role !== 'ADMIN') {
      return (
        <div className="space-y-4">
          <div className="glass-panel rounded-[32px] border p-6">
            <p className="text-xs font-semibold uppercase tracking-[0.25em] text-slate-400">Profile</p>
            <h3 className="mt-3 text-xl font-bold text-slate-900">{currentUser?.name}</h3>
            <p className="text-sm text-slate-500">{currentUser?.email}</p>
            <span className="mt-3 inline-block rounded-full bg-sky-100 px-3 py-1 text-xs font-semibold text-sky-700">
              {currentUser?.role}
            </span>
          </div>
          {renderNotAllowed('User directory locked', 'Only admins can view the full user directory.')}
        </div>
      );
    }

    if (!users.length) {
      return (
        <div className="glass-panel rounded-[32px] border p-6 text-center text-sm text-slate-500">
          Chưa có user nào trong hệ thống.
        </div>
      );
    }

    return (
      <div className="glass-panel rounded-[32px] border p-6">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900">Active Users</h2>
          <span className="rounded-full bg-sky-100 px-3 py-1 text-xs font-semibold text-sky-700">
            {users.length} users
          </span>
        </div>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {users.map((u) => (
            <div key={u.id} className="rounded-3xl border border-slate-200 bg-white/80 p-4">
              <p className="truncate text-sm font-semibold text-slate-900">{u.name}</p>
              <p className="truncate text-xs text-slate-500">{u.email}</p>
              <span className="mt-2 inline-block rounded-full bg-sky-600 px-2 py-0.5 text-[10px] font-semibold text-white">
                {u.role}
              </span>
            </div>
          ))}
        </div>
      </div>
    );
  };

  useEffect(() => {
    const handleHashChange = () => {
      setRoute(window.location.hash.replace('#', '') || DEFAULT_ROUTE);
    };
    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, []);

  useEffect(() => {
    const currentItem = navItems.find((item) => item.path === route);
    if (!currentItem || !isRouteAllowed(currentItem)) {
      window.location.hash = `#${DEFAULT_ROUTE}`;
    }
  }, [route, role]);

  const renderMainContent = () => {
    switch (route) {
      case '/products':
      case '/dashboard':
        return (
          <ProductList
            products={filteredProducts}
            onRefresh={fetchProducts}
            onBuy={createOrder}
            loading={loading}
            currentUser={currentUser}
            onOpenAddModal={() => setIsProductModalOpen(true)}
          />
        );
      case '/orders':
        if (role === 'CUSTOMER' || role === 'ADMIN') {
          return (
            <OrderPanel
              orders={orders}
              loading={loading}
              currentUser={currentUser}
              onRefresh={fetchOrders}
              onCancel={cancelOrder}
            />
          );
        }
        return renderNotAllowed('Orders not available', 'Only customers or admins can access orders.');
      case '/users':
        return renderUsersView();
      case '/admin':
        return role === 'ADMIN'
          ? renderNotAllowed('Admin toolkit', 'Module coming soon. Metrics, policies, and audits.')
          : renderNotAllowed('Admin area locked', 'Admin role is required to access this section.');
      default:
        return (
          <ProductList
            products={filteredProducts}
            onRefresh={fetchProducts}
            onBuy={createOrder}
            loading={loading}
            currentUser={currentUser}
            onOpenAddModal={() => setIsProductModalOpen(true)}
          />
        );
    }
  };

  const extractErrorMessage = (err, fallback) => {
    const payload = err?.response?.data;
    if (typeof payload === 'string') {
      return payload;
    }
    if (payload?.error) {
      return payload.error;
    }
    if (payload?.message) {
      return payload.message;
    }
    return fallback;
  };

  const getAuthHeaders = () => ({
    Authorization: `Bearer ${authToken}`,
  });

  const fetchUsers = async () => {
    try {
      const res = await axios.get(`${GATEWAY_URL}/users`, {
        headers: getAuthHeaders(),
      });
      setUsers(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      showMsg('error', extractErrorMessage(err, 'Không thể tải danh sách user'));
    }
  };

  const fetchProducts = async () => {
    try {
      const res = await axios.get(`${GATEWAY_URL}/products`);
      setProducts(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      showMsg('error', extractErrorMessage(err, 'Không thể tải danh sách sản phẩm'));
    }
  };

  const fetchOrders = async () => {
    try {
      const res = await axios.get(`${GATEWAY_URL}/orders`, {
        headers: getAuthHeaders(),
      });
      setOrders(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      showMsg('error', extractErrorMessage(err, 'Không thể tải danh sách đơn hàng'));
    }
  };

  const handleAuth = async (e) => {
    e.preventDefault();
    setLoading(true);

    try {
      if (isLoginView) {
        const res = await axios.post(`${GATEWAY_URL}/users/login`, {
          email: formData.email,
          password: formData.password,
        });

        const payload = res.data;
        const nextSession = {
          accessToken: payload.access_token,
          tokenType: payload.token_type || 'Bearer',
          expiresAt: payload.expires_at,
          user: payload.user,
        };

        if (!nextSession.accessToken || !nextSession.user) {
          throw new Error('Invalid login response');
        }

        setSession(nextSession);
        localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(nextSession));
        showMsg('success', `Chào mừng quay lại, ${nextSession.user.name}`);
      } else {
        await axios.post(`${GATEWAY_URL}/users`, {
          name: formData.name,
          email: formData.email,
          password: formData.password,
          role: formData.role,
        });

        showMsg('success', 'Đăng ký thành công. Mời bạn đăng nhập.');
        setIsLoginView(true);
      }

      setFormData({ name: '', email: '', password: '', role: 'CUSTOMER' });
    } catch (err) {
      showMsg('error', extractErrorMessage(err, 'Xác thực thất bại'));
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = (customMessage) => {
    setSession(null);
    setUsers([]);
    setOrders([]);
    localStorage.removeItem(SESSION_STORAGE_KEY);
    showMsg('success', customMessage || 'Đăng xuất thành công');
  };

  const handleAddProduct = async (productData) => {
    if (!currentUser || !authToken) {
      showMsg('error', 'Vui lòng đăng nhập');
      return;
    }

    setLoading(true);
    try {
      await axios.post(`${GATEWAY_URL}/products`, productData, {
        headers: getAuthHeaders(),
      });

      showMsg('success', 'Đăng sản phẩm thành công');
      setIsProductModalOpen(false);
      await fetchProducts();
    } catch (err) {
      showMsg('error', extractErrorMessage(err, 'Không thể đăng sản phẩm'));
    } finally {
      setLoading(false);
    }
  };

  const createOrder = async (productId, quantity) => {
    if (!currentUser || !authToken) {
      showMsg('error', 'Vui lòng đăng nhập');
      return;
    }

    if (currentUser.role === 'SELLER') {
      showMsg('error', 'Seller không thể đặt mua sản phẩm');
      return;
    }

    setLoading(true);
    try {
      const idempotencyKey = `order-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      const res = await axios.post(
        `${GATEWAY_URL}/orders`,
        { productId, quantity },
        {
          headers: {
            ...getAuthHeaders(),
            'Idempotency-Key': idempotencyKey,
          },
        }
      );

      if (res.data?.idempotentReplay) {
        showMsg('success', 'Yêu cầu trùng đã được nhận diện, không tạo đơn mới');
      } else {
        showMsg('success', 'Đặt hàng thành công');
      }

      await Promise.all([fetchProducts(), fetchOrders()]);
    } catch (err) {
      showMsg('error', extractErrorMessage(err, 'Không thể tạo đơn hàng'));
    } finally {
      setLoading(false);
    }
  };

  const cancelOrder = async (orderId) => {
    if (!authToken) {
      showMsg('error', 'Vui lòng đăng nhập');
      return;
    }

    setLoading(true);
    try {
      await axios.patch(`${GATEWAY_URL}/orders/${orderId}/cancel`, null, {
        headers: getAuthHeaders(),
      });

      showMsg('success', 'Đơn hàng đã được hủy');
      await Promise.all([fetchProducts(), fetchOrders()]);
    } catch (err) {
      showMsg('error', extractErrorMessage(err, 'Không thể hủy đơn hàng'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-shell min-h-screen px-3 pb-10 pt-4 md:px-6">
      <div className="mx-auto w-full max-w-[1400px]">
        <Message message={message} />

        {!currentUser ? (
          <>
            <Header currentUser={currentUser} session={session} onLogout={handleLogout} />
            <AuthForm
              isLoginView={isLoginView}
              setIsLoginView={setIsLoginView}
              formData={formData}
              setFormData={setFormData}
              handleAuth={handleAuth}
              loading={loading}
            />
          </>
        ) : (
          <div className="grid gap-6 lg:grid-cols-[88px_minmax(0,1fr)_360px]">
            <aside className="glass-panel hidden h-fit flex-col items-center gap-6 rounded-[32px] border px-4 py-6 text-slate-600 lg:flex">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-sky-600 text-white shadow-lg">
                <ShoppingCart className="h-6 w-6" />
              </div>
              <nav className="flex flex-col items-center gap-4">
                {navItems.map((item) => {
                  const isAllowed = isRouteAllowed(item);
                  const Icon = item.icon;
                  if (!isAllowed) {
                    return (
                      <div
                        key={item.label}
                        className="flex h-11 w-11 items-center justify-center rounded-2xl border border-transparent bg-white/50 text-slate-300"
                        title={`${item.label} (Restricted)`}
                      >
                        <Icon className="h-5 w-5" />
                      </div>
                    );
                  }

                  const isActive = route === item.path;
                  return (
                    <a
                      key={item.label}
                      href={`#${item.path}`}
                      className={`group flex h-11 w-11 items-center justify-center rounded-2xl border text-slate-500 shadow-sm transition hover:-translate-y-0.5 ${
                        isActive
                          ? 'border-sky-300 bg-sky-600 text-white shadow-md shadow-sky-200'
                          : 'border-transparent bg-white/70 hover:border-sky-200 hover:bg-white hover:text-sky-700'
                      }`}
                      title={item.label}
                    >
                      <Icon className="h-5 w-5" />
                    </a>
                  );
                })}
              </nav>
              <div className="mt-auto flex h-11 w-11 items-center justify-center rounded-2xl bg-sky-100 text-sky-600">
                <ShoppingCart className="h-5 w-5" />
              </div>
            </aside>

            <main className="space-y-6">
              <div className="glass-panel flex flex-col gap-4 rounded-[32px] border px-5 py-4 md:flex-row md:items-center md:justify-between">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.3em] text-sky-600">
                    Store Operations
                  </p>
                  <h1 className="mt-2 text-2xl font-bold text-slate-900 md:text-3xl">
                    Mini Ecommerce Control Room
                  </h1>
                  <p className="mt-1 text-sm text-slate-500">
                    Theo dõi sản phẩm, đơn hàng và trạng thái realtime của hệ thống.
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <div className="flex items-center gap-2 rounded-full border border-sky-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600">
                    <span className="h-2 w-2 rounded-full bg-emerald-400" />
                    Live sync
                  </div>
                  <div className="rounded-full border border-sky-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600">
                    {currentUser.name} · {currentUser.role}
                  </div>
                  <button
                    type="button"
                    onClick={() => handleLogout()}
                    className="rounded-full bg-slate-900 px-4 py-2 text-xs font-semibold text-white transition hover:bg-slate-700"
                  >
                    Đăng xuất
                  </button>
                </div>
              </div>

              {showSearchBar && (
                <div className="glass-panel rounded-[32px] border px-5 py-4">
                  <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                    <div className="relative flex-1">
                      <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-sky-500" />
                      <input
                        type="text"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        placeholder="Search products, vendors, or keywords..."
                        className="w-full rounded-2xl border border-sky-100 bg-white py-3 pl-11 pr-4 text-sm font-medium text-slate-700 outline-none transition focus:border-sky-300 focus:ring-2 focus:ring-sky-200"
                      />
                    </div>
                    <div className="flex flex-wrap items-center gap-3">
                      <div className="rounded-2xl bg-sky-50 px-4 py-3 text-xs font-semibold text-sky-700">
                        Products: {products.length}
                      </div>
                      <div className="rounded-2xl bg-sky-50 px-4 py-3 text-xs font-semibold text-sky-700">
                        Orders: {orders.length}
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {renderMainContent()}
            </main>

            <aside className="space-y-6">
              <UserSidebar currentUser={currentUser} users={users} orders={orders} />
              {showOrderSidebar && (
                <OrderPanel
                  orders={orders}
                  loading={loading}
                  currentUser={currentUser}
                  onRefresh={fetchOrders}
                  onCancel={cancelOrder}
                />
              )}
            </aside>
          </div>
        )}

        <ProductForm
          isOpen={isProductModalOpen}
          onClose={() => setIsProductModalOpen(false)}
          onSubmit={handleAddProduct}
          loading={loading}
        />
      </div>
    </div>
  );
}

export default App;
