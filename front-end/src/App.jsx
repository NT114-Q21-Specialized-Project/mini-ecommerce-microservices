import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';

import Header from './components/Layout/Header';
import Message from './components/Layout/Message';
import AuthForm from './components/Auth/AuthForm';
import UserSidebar from './components/User/UserSidebar';
import ProductList from './components/Product/ProductList';
import ProductForm from './components/Product/ProductForm';
import OrderPanel from './components/Order/OrderPanel';

const GATEWAY_URL = 'http://localhost:9000/api';
const SESSION_STORAGE_KEY = 'mini-ecom-session';

function App() {
  const [isLoginView, setIsLoginView] = useState(true);
  const [session, setSession] = useState(null);
  const [users, setUsers] = useState([]);
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    role: 'CUSTOMER',
  });
  const [isProductModalOpen, setIsProductModalOpen] = useState(false);

  const currentUser = session?.user || null;
  const authToken = session?.accessToken || '';

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
    <div className="app-shell min-h-screen px-4 pb-12 pt-6 md:px-8">
      <div className="mx-auto w-full max-w-7xl">
        <Header currentUser={currentUser} session={session} onLogout={handleLogout} />
        <Message message={message} />

        {!currentUser ? (
          <AuthForm
            isLoginView={isLoginView}
            setIsLoginView={setIsLoginView}
            formData={formData}
            setFormData={setFormData}
            handleAuth={handleAuth}
            loading={loading}
          />
        ) : (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <UserSidebar currentUser={currentUser} users={users} orders={orders} />
            <div className="space-y-6 lg:col-span-2">
              <ProductList
                products={products}
                onRefresh={fetchProducts}
                onBuy={createOrder}
                loading={loading}
                currentUser={currentUser}
                onOpenAddModal={() => setIsProductModalOpen(true)}
              />

              {(currentUser.role === 'CUSTOMER' || currentUser.role === 'ADMIN') && (
                <OrderPanel
                  orders={orders}
                  loading={loading}
                  currentUser={currentUser}
                  onRefresh={fetchOrders}
                  onCancel={cancelOrder}
                />
              )}
            </div>
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
