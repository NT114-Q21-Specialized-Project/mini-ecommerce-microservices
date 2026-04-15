import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import { BellDot, Boxes, CreditCard, GitBranch, ListOrdered, LogOut, Search, ShieldCheck, ShoppingBag, Store, Users2 } from 'lucide-react';

import Message from './components/Layout/Message';
import MediaSlot from './components/Layout/MediaSlot';
import AuthForm from './components/Auth/AuthForm';
import UserSidebar from './components/User/UserSidebar';
import ProductList from './components/Product/ProductList';
import ProductForm from './components/Product/ProductForm';
import { pickFeaturedProducts, sortProductsByNewest } from './components/Product/productMedia';

const GATEWAY_URL = (import.meta.env.VITE_GATEWAY_URL || '/api/v1').replace(/\/$/, '');
const SESSION_STORAGE_KEY = 'mini-ecom-session';
const DEFAULT_ROUTE = '/dashboard';

const navItems = [
  { path: '/dashboard', label: 'Dashboard', icon: ShoppingBag },
  { path: '/products', label: 'Products', icon: Store },
  { path: '/inventory', label: 'Inventory', icon: Boxes },
  { path: '/orders', label: 'Orders', icon: ListOrdered, roles: ['CUSTOMER', 'ADMIN'] },
  { path: '/saga', label: 'Saga Trace', icon: GitBranch, roles: ['CUSTOMER', 'ADMIN'] },
  { path: '/users', label: 'Users', icon: Users2 },
  { path: '/admin', label: 'Admin', icon: ShieldCheck, roles: ['ADMIN'] },
];

const routeContent = {
  '/dashboard': {
    eyebrow: 'Welcome back',
    description: 'Explore now and keep the catalog, checkout flow, and orders in one clean board.',
    heroEyebrow: 'New Arrival',
    heroTitle: 'Just for you!',
    heroDescription: 'Change your daily life with a modern lifestyle.',
    searchPlaceholder: 'Search products, sellers, or keywords...',
  },
  '/products': {
    eyebrow: 'Welcome back',
    description: 'Explore now and browse the highlighted product wall.',
    heroEyebrow: 'New Arrival',
    heroTitle: 'Just for you!',
    heroDescription: 'Change your daily life with a modern lifestyle.',
    searchPlaceholder: 'Search Anything...',
  },
  '/inventory': {
    eyebrow: 'Inventory Snapshot',
    description: 'Check available stock and keep the product wall healthy.',
    heroEyebrow: 'Stock Overview',
    heroTitle: 'Inventory focus',
    heroDescription: 'Refresh the latest numbers before the next order wave.',
    searchPlaceholder: 'Search inventory...',
  },
  '/orders': {
    eyebrow: 'Order Stream',
    description: 'Theo dõi đơn hàng gần nhất, tổng giá trị và những order đang chờ xử lý.',
    heroEyebrow: 'Fulfillment View',
    heroTitle: 'A calmer way to watch every checkout move.',
    heroDescription: 'Thông tin đơn hàng được gom lại để dễ đọc, dễ kiểm soát và dễ hủy khi cần.',
  },
  '/saga': {
    eyebrow: 'Saga Explorer',
    description: 'Theo dấu từng step trong order saga và timeline thanh toán theo cách trực quan hơn.',
    heroEyebrow: 'Distributed Trace',
    heroTitle: 'Open the story behind every order flow.',
    heroDescription: 'Từ từng step đến payment event, mọi chuyển động đều nằm trong một màn hình rõ ràng.',
  },
  '/users': {
    eyebrow: 'People Directory',
    description: 'Giữ thông tin user hiện tại và user list ở một vùng nhìn tập trung hơn.',
    heroEyebrow: 'Team Access',
    heroTitle: 'Profile and operators, without the clutter.',
    heroDescription: 'Khu vực này thiên về nhân sự hệ thống, quyền truy cập và tình trạng user hiện hành.',
  },
  '/admin': {
    eyebrow: 'Admin Space',
    description: 'Khu vực dành cho metrics, policy và audit khi module này được mở rộng thêm.',
    heroEyebrow: 'Control Layer',
    heroTitle: 'Reserved for deeper platform controls.',
    heroDescription: 'Phần nền đã sẵn để mình mở rộng tiếp cho governance, policy và admin tooling.',
  },
};

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
  const [inventorySnapshots, setInventorySnapshots] = useState([]);
  const [selectedSagaOrderId, setSelectedSagaOrderId] = useState('');
  const [sagaSteps, setSagaSteps] = useState([]);
  const [paymentTimeline, setPaymentTimeline] = useState([]);
  const [sagaLoading, setSagaLoading] = useState(false);
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

  const featuredProducts = useMemo(() => pickFeaturedProducts(filteredProducts, 4), [filteredProducts]);

  const currentRouteContent = routeContent[route] || routeContent[DEFAULT_ROUTE];
  const greetingName = currentUser?.name?.trim()?.split(/\s+/)?.[0] || 'Operator';
  const liveProductCount = products.filter((product) => Number(product.stock || 0) > 0).length;
  const lowStockCount = products.filter((product) => {
    const stock = Number(product.stock || 0);
    return stock > 0 && stock <= 5;
  }).length;
  const totalCatalogValue = products.reduce(
    (sum, product) => sum + Number(product.price || 0) * Math.max(Number(product.stock || 0), 1),
    0
  );
  const pendingOrderCount = orders.filter((order) => !['CONFIRMED', 'FAILED', 'CANCELLED'].includes(order.status)).length;

  const isRouteAllowed = (item) => !item.roles || (role && item.roles.includes(role));
  const allowedNavItems = navItems.filter(isRouteAllowed);
  const showSearchBar = ['/dashboard', '/products', '/inventory'].includes(route);
  const showRightRail = true;

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

  const fetchInventorySnapshots = async () => {
    if (!authToken || !products.length) {
      setInventorySnapshots([]);
      return;
    }

    try {
      const requests = products.slice(0, 40).map((product) =>
        axios.get(`${GATEWAY_URL}/inventory/${product.id}`, {
          headers: getAuthHeaders(),
        })
      );
      const settled = await Promise.allSettled(requests);
      const snapshots = settled
        .filter((result) => result.status === 'fulfilled')
        .map((result) => result.value.data);
      setInventorySnapshots(snapshots);
    } catch (err) {
      showMsg('error', extractErrorMessage(err, 'Không thể tải inventory snapshot'));
    }
  };

  const fetchSagaForOrder = async (orderId) => {
    if (!orderId || !authToken) {
      setSagaSteps([]);
      setPaymentTimeline([]);
      return;
    }

    setSagaLoading(true);
    try {
      const [stepsRes, paymentsRes] = await Promise.all([
        axios.get(`${GATEWAY_URL}/orders/${orderId}/saga`, {
          headers: getAuthHeaders(),
        }),
        role === 'ADMIN'
          ? axios.get(`${GATEWAY_URL}/payments/order/${orderId}`, {
              headers: getAuthHeaders(),
            })
          : Promise.resolve({ data: [] }),
      ]);

      setSagaSteps(Array.isArray(stepsRes.data) ? stepsRes.data : []);
      setPaymentTimeline(Array.isArray(paymentsRes.data) ? paymentsRes.data : []);
    } catch (err) {
      showMsg('error', extractErrorMessage(err, 'Không thể tải saga trace'));
    } finally {
      setSagaLoading(false);
    }
  };

  useEffect(() => {
    if (route !== '/inventory') {
      return;
    }
    fetchInventorySnapshots();
  }, [route, products, authToken]);

  useEffect(() => {
    if (route !== '/saga') {
      return;
    }
    if (!orders.length) {
      setSelectedSagaOrderId('');
      setSagaSteps([]);
      setPaymentTimeline([]);
      return;
    }

    const exists = orders.some((order) => order.id === selectedSagaOrderId);
    const nextOrderId = exists ? selectedSagaOrderId : orders[0]?.id;
    if (!nextOrderId) {
      return;
    }
    setSelectedSagaOrderId(nextOrderId);
    fetchSagaForOrder(nextOrderId);
  }, [route, orders, selectedSagaOrderId, authToken]);

  const renderInventoryView = () => {
    const stockMap = new Map();
    inventorySnapshots.forEach((item) => {
      stockMap.set(item.productId, item.availableStock);
    });

    return (
      <section className="glass-panel rounded-[32px] border p-5 md:p-6">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900 md:text-xl">
            <Boxes className="h-5 w-5 text-sky-600" />
            Inventory Snapshot
          </h2>
          <button
            type="button"
            onClick={fetchInventorySnapshots}
            className="rounded-2xl border border-sky-200 bg-white px-3 py-2 text-xs font-semibold text-sky-700 transition hover:bg-sky-50"
          >
            Refresh Inventory
          </button>
        </div>

        {products.length === 0 ? (
          <div className="rounded-3xl border border-dashed border-slate-200 bg-white/70 p-8 text-center text-sm text-slate-500">
            Chưa có sản phẩm để hiển thị tồn kho.
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {products.map((product) => (
              <article key={product.id} className="rounded-3xl border border-slate-200 bg-white/80 p-4">
                <p className="truncate text-sm font-semibold text-slate-900">{product.name}</p>
                <p className="mt-1 text-xs text-slate-500">Product ID: {product.id?.slice(0, 8)}...</p>
                <div className="mt-3 flex items-center justify-between">
                  <span className="text-lg font-bold text-slate-900">${Number(product.price || 0).toFixed(2)}</span>
                  <span className="rounded-full bg-sky-100 px-3 py-1 text-xs font-semibold text-sky-700">
                    Stock: {stockMap.has(product.id) ? stockMap.get(product.id) : product.stock}
                  </span>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    );
  };

  const renderOrdersView = () => (
    <section className="fashion-panel rounded-[30px] border p-5 md:p-6">
      <div className="mb-5 flex items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.26em] text-sky-600">Order Stream</p>
          <h2 className="mt-2 text-2xl font-bold text-slate-900">Latest Orders</h2>
        </div>
        <button
          type="button"
          onClick={fetchOrders}
          className="rounded-full border border-sky-100 bg-white px-4 py-2 text-sm font-semibold text-sky-700 transition hover:bg-sky-50"
        >
          Refresh
        </button>
      </div>

      {orders.length === 0 ? (
        <div className="rounded-[24px] border border-dashed border-slate-200 bg-slate-50/80 p-10 text-center text-sm text-slate-500">
          Chưa có đơn hàng nào.
        </div>
      ) : (
        <div className="space-y-3">
          {orders.map((order) => (
            <article key={order.id} className="rounded-[24px] border border-slate-100 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="font-mono text-xs text-slate-400">{order.id?.slice(0, 8)}...</p>
                  <p className="mt-1 text-sm font-semibold text-slate-900">{order.status}</p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-slate-400">Total</p>
                  <p className="text-lg font-bold text-slate-900">${Number(order.totalAmount || 0).toFixed(2)}</p>
                </div>
              </div>
              <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-slate-500">
                <span>Quantity: {order.quantity}</span>
                <span>Unit: ${Number(order.unitPrice || 0).toFixed(2)}</span>
                <button
                  type="button"
                  disabled={loading}
                  onClick={() => cancelOrder(order.id)}
                  className="rounded-full bg-slate-950 px-4 py-2 text-xs font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
                >
                  Cancel Order
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );

  const renderSagaView = () => (
    <section className="glass-panel rounded-[32px] border p-5 md:p-6">
      <div className="mb-5 flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900 md:text-xl">
          <GitBranch className="h-5 w-5 text-sky-600" />
          Saga Trace Explorer
        </h2>
        <button
          type="button"
          onClick={() => selectedSagaOrderId && fetchSagaForOrder(selectedSagaOrderId)}
          className="rounded-2xl border border-sky-200 bg-white px-3 py-2 text-xs font-semibold text-sky-700 transition hover:bg-sky-50"
          disabled={!selectedSagaOrderId}
        >
          Refresh Trace
        </button>
      </div>

      {orders.length === 0 ? (
        <div className="rounded-3xl border border-dashed border-slate-200 bg-white/70 p-8 text-center text-sm text-slate-500">
          Chưa có order để hiển thị saga.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-[260px_minmax(0,1fr)]">
          <aside className="space-y-2 rounded-3xl border border-slate-200 bg-white/70 p-3">
            {orders.slice(0, 20).map((order) => (
              <button
                key={order.id}
                type="button"
                onClick={() => {
                  setSelectedSagaOrderId(order.id);
                  fetchSagaForOrder(order.id);
                }}
                className={`w-full rounded-2xl border px-3 py-2 text-left text-xs transition ${
                  selectedSagaOrderId === order.id
                    ? 'border-sky-300 bg-sky-50 text-sky-800'
                    : 'border-transparent bg-white text-slate-600 hover:border-sky-200'
                }`}
              >
                <p className="font-mono text-[11px]">{order.id?.slice(0, 8)}...</p>
                <p className="mt-1 font-semibold">{order.status}</p>
              </button>
            ))}
          </aside>

          <div className="space-y-4">
            <div className="rounded-3xl border border-slate-200 bg-white/80 p-4">
              <p className="mb-3 text-xs font-semibold uppercase tracking-[0.25em] text-slate-400">Saga Steps</p>
              {sagaLoading ? (
                <p className="text-sm text-slate-500">Loading saga steps...</p>
              ) : sagaSteps.length === 0 ? (
                <p className="text-sm text-slate-500">Không có step cho order này.</p>
              ) : (
                <div className="space-y-2">
                  {sagaSteps.map((step) => (
                    <div key={step.id} className="rounded-2xl border border-slate-200 bg-white p-3 text-xs text-slate-600">
                      <div className="flex items-center justify-between gap-2">
                        <p className="font-semibold text-slate-900">{step.stepName}</p>
                        <span
                          className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                            step.stepStatus === 'SUCCESS'
                              ? 'bg-emerald-100 text-emerald-700'
                              : step.stepStatus === 'RETRY_FAILED'
                                ? 'bg-amber-100 text-amber-700'
                                : 'bg-rose-100 text-rose-700'
                          }`}
                        >
                          {step.stepStatus}
                        </span>
                      </div>
                      <p className="mt-1">retry: {step.retryCount} · compensation: {step.compensation ? 'yes' : 'no'}</p>
                      <p className="mt-1 text-slate-500">{step.detail || '-'}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="rounded-3xl border border-slate-200 bg-white/80 p-4">
              <p className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.25em] text-slate-400">
                <CreditCard className="h-4 w-4 text-sky-600" />
                Payment Timeline
              </p>
              {paymentTimeline.length === 0 ? (
                <p className="text-sm text-slate-500">
                  {role === 'ADMIN' ? 'No payment events for this order.' : 'Payment timeline is visible for admin only.'}
                </p>
              ) : (
                <div className="space-y-2">
                  {paymentTimeline.map((row) => (
                    <div key={row.id} className="rounded-2xl border border-slate-200 bg-white p-3 text-xs text-slate-600">
                      <div className="flex items-center justify-between">
                        <p className="font-semibold text-slate-900">{row.operationType}</p>
                        <span className="rounded-full bg-sky-100 px-2 py-0.5 text-[10px] font-semibold text-sky-700">
                          {row.status}
                        </span>
                      </div>
                      <p className="mt-1">${Number(row.amount || 0).toFixed(2)} {row.currency}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </section>
  );

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
      case '/dashboard':
        return (
          <ProductList
            products={featuredProducts}
            onRefresh={fetchProducts}
            onBuy={createOrder}
            loading={loading}
            currentUser={currentUser}
            onOpenAddModal={() => setIsProductModalOpen(true)}
            compact
          />
        );
      case '/products':
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
      case '/inventory':
        return renderInventoryView();
      case '/orders':
        if (role === 'CUSTOMER' || role === 'ADMIN') {
          return renderOrdersView();
        }
        return renderNotAllowed('Orders not available', 'Only customers or admins can access orders.');
      case '/saga':
        if (role === 'CUSTOMER' || role === 'ADMIN') {
          return renderSagaView();
        }
        return renderNotAllowed('Saga trace not available', 'Only customers or admins can access saga traces.');
      case '/users':
        return renderUsersView();
      case '/admin':
        return role === 'ADMIN'
          ? renderNotAllowed('Admin toolkit', 'Module coming soon. Metrics, policies, and audits.')
          : renderNotAllowed('Admin area locked', 'Admin role is required to access this section.');
      default:
        return (
          <ProductList
            products={featuredProducts}
            onRefresh={fetchProducts}
            onBuy={createOrder}
            loading={loading}
            currentUser={currentUser}
            onOpenAddModal={() => setIsProductModalOpen(true)}
            compact
          />
        );
    }
  };

  const extractErrorMessage = (err, fallback) => {
    const payload = err?.response?.data;
    if (typeof payload === 'string') {
      return payload;
    }
    if (typeof payload?.error === 'string') {
      return payload.error;
    }
    if (payload?.error?.message) {
      return payload.error.message;
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
      if (Array.isArray(res.data)) {
        setUsers(res.data);
        return;
      }

      if (Array.isArray(res.data?.items)) {
        setUsers(res.data.items);
        return;
      }

      setUsers([]);
    } catch (err) {
      showMsg('error', extractErrorMessage(err, 'Không thể tải danh sách user'));
    }
  };

  const fetchProducts = async () => {
    try {
      const res = await axios.get(`${GATEWAY_URL}/products`, {
        params: {
          page: 0,
          size: 100,
          sortBy: 'createdAt',
          sortDir: 'desc',
        },
      });
      if (Array.isArray(res.data)) {
        setProducts(sortProductsByNewest(res.data));
        return;
      }

      if (Array.isArray(res.data?.items)) {
        setProducts(sortProductsByNewest(res.data.items));
        return;
      }

      setProducts([]);
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
    <div
      className={`app-shell min-h-screen ${currentUser ? 'px-3 pb-10 pt-4 md:px-6' : 'px-3 py-5 md:px-6 md:py-8'}`}
    >
      <div className={`mx-auto w-full ${currentUser ? 'max-w-[1490px]' : 'max-w-[1180px]'}`}>
        <Message message={message} />

        {!currentUser ? (
          <section className="auth-stage">
            <div className="auth-frame">
              <AuthForm
                isLoginView={isLoginView}
                setIsLoginView={setIsLoginView}
                formData={formData}
                setFormData={setFormData}
                handleAuth={handleAuth}
                loading={loading}
              />
            </div>
          </section>
        ) : (
          <div className={`fashion-dashboard-grid ${showRightRail ? 'has-order-rail' : 'no-order-rail'}`}>
            <UserSidebar products={products} navItems={allowedNavItems} route={route} />

            <main className="space-y-5">
              {(route === '/dashboard' || route === '/products') && (
                <section className="fashion-hero-card rounded-[30px] border p-6 md:p-7">
                  <MediaSlot
                    src="/dashboard-media/hero/hero-main.png"
                    alt="Hero dashboard artwork"
                    title="Hero artwork"
                    hint="hero-main.png"
                    className="fashion-hero-cover h-[300px] rounded-[28px] md:h-[360px]"
                    imgClassName="h-full w-full object-cover"
                  />
                </section>
              )}

              {renderMainContent()}

              {route === '/inventory' ? (
                <div className="grid gap-3 md:grid-cols-3">
                  <div className="fashion-metric-card rounded-[24px] p-5">
                    <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">Products</p>
                    <p className="mt-4 text-3xl font-bold text-slate-900">{liveProductCount}</p>
                  </div>
                  <div className="fashion-metric-card rounded-[24px] p-5">
                    <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">Low Stock</p>
                    <p className="mt-4 text-3xl font-bold text-slate-900">{lowStockCount}</p>
                  </div>
                  <div className="fashion-metric-card rounded-[24px] p-5">
                    <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">Catalog Value</p>
                    <p className="mt-4 text-3xl font-bold text-slate-900">${totalCatalogValue.toFixed(0)}</p>
                  </div>
                </div>
              ) : null}
            </main>

            {showRightRail ? (
              <aside className="space-y-5">
                <section className="fashion-panel dashboard-control-rail rounded-[34px] border p-6">
                  <div>
                    <h1 className="text-[34px] font-bold leading-none text-slate-900">Hello, {greetingName}</h1>
                    <p className="mt-3 text-sm leading-7 text-slate-400">{currentRouteContent.description}</p>
                  </div>

                  <div className="mt-6 space-y-3">
                    {showSearchBar ? (
                      <div className="relative">
                        <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-sky-400" />
                        <input
                          type="text"
                          value={searchTerm}
                          onChange={(e) => setSearchTerm(e.target.value)}
                          placeholder={currentRouteContent.searchPlaceholder}
                          className="dashboard-input w-full rounded-full py-3 pl-11 pr-4 text-sm font-medium text-slate-700 outline-none"
                        />
                      </div>
                    ) : null}

                    <button
                      type="button"
                      className="inline-flex h-12 w-12 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 transition hover:text-sky-600"
                      aria-label="Notifications"
                    >
                      <BellDot className="h-4 w-4" />
                    </button>
                  </div>

                  <div className="mt-6 rounded-[24px] border border-slate-100 bg-white px-4 py-4 shadow-sm">
                    <div className="flex items-center gap-3">
                      <div className="flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-cyan-300 to-blue-600 text-xl font-bold text-white">
                        {(currentUser?.name || 'U').trim().charAt(0).toUpperCase()}
                      </div>
                      <div className="min-w-0">
                        <p className="truncate text-lg font-semibold text-slate-900">{currentUser?.name}</p>
                        <p className="truncate text-sm text-slate-500">{currentUser?.email}</p>
                      </div>
                    </div>
                  </div>

                  <div className="mt-6 space-y-3">
                    <div className="inline-flex rounded-full bg-emerald-50 px-4 py-2 text-sm font-semibold text-emerald-600">
                      <span className="inline-flex items-center gap-2">
                        <span className="h-2 w-2 rounded-full bg-emerald-400" />
                        Live sync
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleLogout()}
                      className="inline-flex w-full items-center justify-center gap-2 rounded-full bg-slate-950 px-5 py-3 text-xs font-semibold uppercase tracking-[0.2em] text-white transition hover:bg-slate-800"
                    >
                      <LogOut className="h-4 w-4" />
                      Logout
                    </button>
                  </div>
                </section>
              </aside>
            ) : null}

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
