import React, { useState, useEffect } from 'react';
import axios from 'axios';

// Import Components
import Header from './components/Layout/Header';
import Message from './components/Layout/Message';
import AuthForm from './components/Auth/AuthForm';
import UserSidebar from './components/User/UserSidebar';
import ProductList from './components/Product/ProductList';
import ProductForm from './components/Product/ProductForm';

const GATEWAY_URL = "http://localhost:9000/api";

function App() {
  const [isLoginView, setIsLoginView] = useState(true);
  const [currentUser, setCurrentUser] = useState(null);
  const [users, setUsers] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [formData, setFormData] = useState({ name: '', email: '', password: '', role: 'CUSTOMER' });
  const [isProductModalOpen, setIsProductModalOpen] = useState(false);

  useEffect(() => {
    const savedUser = localStorage.getItem('user');
    if (savedUser) setCurrentUser(JSON.parse(savedUser));
    fetchProducts();
  }, []);

  useEffect(() => {
    if (currentUser?.role === 'ADMIN') fetchUsers();
  }, [currentUser]);

  const showMsg = (type, text) => {
    setMessage({ type, text });
    setTimeout(() => setMessage({ type: '', text: '' }), 4000);
  };

  const fetchUsers = async () => {
    try {
      const res = await axios.get(`${GATEWAY_URL}/users`);
      setUsers(res.data);
    } catch (err) { console.error("Error fetching users", err); }
  };

  const fetchProducts = async () => {
    try {
      const res = await axios.get(`${GATEWAY_URL}/products`);
      setProducts(res.data);
    } catch (err) { console.error("Error fetching products", err); }
  };

  const handleAuth = async (e) => {
    e.preventDefault();
    setLoading(true);
    const endpoint = isLoginView ? '/users/login' : '/users/register';
    try {
      const res = await axios.post(`${GATEWAY_URL}${endpoint}`, formData);
      if (isLoginView) {
        setCurrentUser(res.data);
        localStorage.setItem('user', JSON.stringify(res.data));
        showMsg('success', `Chào mừng ${res.data.name}!`);
      } else {
        showMsg('success', 'Đăng ký thành công! Hãy đăng nhập.');
        setIsLoginView(true);
      }
    } catch (err) {
      showMsg('error', err.response?.data || 'Lỗi xác thực');
    } finally { setLoading(false); }
  };

  const handleLogout = () => {
    setCurrentUser(null);
    localStorage.removeItem('user');
    showMsg('success', 'Đã đăng xuất');
  };

  const handleAddProduct = async (productData) => {
    if (!currentUser) return showMsg('error', 'Vui lòng đăng nhập!');
    
    setLoading(true);
    try {
      // CẬP NHẬT: Gửi kèm Header X-User-Id để Backend định danh Seller
      await axios.post(`${GATEWAY_URL}/products`, productData, {
        headers: {
          'X-User-Id': currentUser.id
        }
      });
      showMsg('success', 'Đã đăng sản phẩm thành công!');
      setIsProductModalOpen(false);
      fetchProducts();
    } catch (err) {
      // Hiển thị lỗi chi tiết từ Backend nếu có
      const errMsg = typeof err.response?.data === 'string' 
        ? err.response.data 
        : (err.response?.data?.message || 'Lỗi khi đăng sản phẩm');
      showMsg('error', errMsg);
    } finally {
      setLoading(false);
    }
  };

  const createOrder = async (productId, price) => {
    if (!currentUser) return showMsg('error', 'Vui lòng đăng nhập!');
    if (currentUser.role === 'ADMIN') return showMsg('error', 'Admin không thể mua hàng!');

    setLoading(true);
    try {
      await axios.post(`${GATEWAY_URL}/orders`, null, {
        params: { userId: currentUser.id, productId, quantity: 1, totalAmount: price }
      });
      showMsg('success', `Đặt hàng thành công!`);
      fetchProducts();
    } catch (err) {
      showMsg('error', err.response?.data || 'Lỗi đặt hàng');
    } finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen bg-gray-50 p-4 md:p-8 font-sans">
      <Header currentUser={currentUser} onLogout={handleLogout} />
      <Message message={message} />

      {!currentUser ? (
        <AuthForm 
          isLoginView={isLoginView} setIsLoginView={setIsLoginView}
          formData={formData} setFormData={setFormData}
          handleAuth={handleAuth} loading={loading}
        />
      ) : (
        <div className="max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-3 gap-8">
          <UserSidebar currentUser={currentUser} users={users} />
          <ProductList 
            products={products} 
            onRefresh={fetchProducts} 
            onBuy={createOrder} 
            loading={loading}
            currentUser={currentUser}
            onOpenAddModal={() => setIsProductModalOpen(true)}
          />
        </div>
      )}

      <ProductForm 
        isOpen={isProductModalOpen} 
        onClose={() => setIsProductModalOpen(false)}
        onSubmit={handleAddProduct}
        loading={loading}
      />
    </div>
  );
}

export default App;