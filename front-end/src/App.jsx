import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { 
  ShoppingCart, User, Package, Plus, CheckCircle, 
  AlertCircle, RefreshCcw, LogIn, UserPlus, LogOut, ShieldCheck 
} from 'lucide-react';

const GATEWAY_URL = "http://localhost:9000/api";

// === TÁCH RA NGOÀI ĐỂ TRÁNH RESET KHI GÕ PHÍM ===
const AuthForm = ({ isLoginView, setIsLoginView, formData, setFormData, handleAuth, loading }) => (
  <div className="max-w-md mx-auto bg-white p-8 rounded-3xl shadow-xl border border-gray-100 mt-10">
    <div className="flex justify-center mb-6">
      <div className="p-3 bg-indigo-100 rounded-full text-indigo-600">
        {isLoginView ? <LogIn size={32} /> : <UserPlus size={32} />}
      </div>
    </div>
    <h2 className="text-2xl font-bold text-center text-gray-800 mb-6 animate-pulse">
      {isLoginView ? 'Đăng nhập hệ thống' : 'Tạo tài khoản mới'}
    </h2>
    <form onSubmit={handleAuth} className="space-y-4">
      {!isLoginView && (
        <input 
          type="text" placeholder="Họ và tên" required
          value={formData.name}
          className="w-full p-3 rounded-xl border border-gray-200 focus:ring-2 focus:ring-indigo-500 outline-none"
          onChange={e => setFormData({...formData, name: e.target.value})}
        />
      )}
      <input 
        type="email" placeholder="Email" required
        value={formData.email}
        className="w-full p-3 rounded-xl border border-gray-200 focus:ring-2 focus:ring-indigo-500 outline-none"
        onChange={e => setFormData({...formData, email: e.target.value})}
      />
      <input 
        type="password" placeholder="Mật khẩu" required
        value={formData.password}
        className="w-full p-3 rounded-xl border border-gray-200 focus:ring-2 focus:ring-indigo-500 outline-none"
        onChange={e => setFormData({...formData, password: e.target.value})}
      />
      {!isLoginView && (
        <select 
          value={formData.role}
          className="w-full p-3 rounded-xl border border-gray-200 outline-none bg-gray-50"
          onChange={e => setFormData({...formData, role: e.target.value})}
        >
          <option value="CUSTOMER">Customer (Người mua)</option>
          <option value="SELLER">Seller (Người bán)</option>
        </select>
      )}
      <button 
        disabled={loading}
        type="submit"
        className="w-full bg-indigo-600 text-white p-3 rounded-xl font-bold hover:bg-indigo-700 transition-all active:scale-95 shadow-lg shadow-indigo-200"
      >
        {loading ? 'Đang xử lý...' : isLoginView ? 'Đăng nhập' : 'Đăng ký ngay'}
      </button>
    </form>
    <p className="text-center mt-6 text-sm text-gray-500">
      {isLoginView ? "Chưa có tài khoản?" : "Đã có tài khoản?"}
      <button 
        onClick={() => setIsLoginView(!isLoginView)}
        className="ml-2 text-indigo-600 font-bold hover:underline"
      >
        {isLoginView ? 'Đăng ký' : 'Đăng nhập'}
      </button>
    </p>
  </div>
);

function App() {
  const [isLoginView, setIsLoginView] = useState(true);
  const [currentUser, setCurrentUser] = useState(null);
  const [users, setUsers] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [formData, setFormData] = useState({ name: '', email: '', password: '', role: 'CUSTOMER' });

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
      <header className="mb-10 flex flex-col items-center">
        <h1 className="text-4xl font-extrabold text-indigo-600 tracking-tight">Mini Ecommerce</h1>
        {currentUser && (
          <div className="mt-4 flex items-center bg-white px-4 py-2 rounded-full shadow-sm border border-gray-100">
            <div className={`w-2 h-2 rounded-full mr-2 ${currentUser.role === 'ADMIN' ? 'bg-red-500' : 'bg-green-500'}`}></div>
            <span className="text-sm font-medium text-gray-700 mr-4">
              {currentUser.name} ({currentUser.role})
            </span>
            <button onClick={handleLogout} className="text-gray-400 hover:text-red-500 transition-colors">
              <LogOut size={16} />
            </button>
          </div>
        )}
      </header>

      {message.text && (
        <div className={`fixed top-5 right-5 z-50 p-4 rounded-xl shadow-2xl flex items-center animate-bounce text-white ${message.type === 'success' ? 'bg-green-500' : 'bg-red-500'}`}>
          {message.type === 'success' ? <CheckCircle className="mr-2" /> : <AlertCircle className="mr-2" />}
          {message.text}
        </div>
      )}

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
        <div className="max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-3 gap-8">
          <section className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
            <h2 className="text-xl font-bold mb-6 flex items-center">
              {currentUser.role === 'ADMIN' ? <ShieldCheck className="text-red-500 mr-2" /> : <User className="text-indigo-500 mr-2" />}
              {currentUser.role === 'ADMIN' ? 'Quản trị hệ thống' : 'Thông tin cá nhân'}
            </h2>
            {currentUser.role === 'ADMIN' ? (
              <div className="space-y-3 max-h-[400px] overflow-y-auto pr-2">
                <p className="text-xs font-bold text-gray-400 uppercase tracking-widest">Danh sách User</p>
                {users.map(u => (
                  <div key={u.id} className="p-3 bg-gray-50 rounded-xl border border-gray-100">
                    <p className="font-bold text-sm text-gray-800">{u.name}</p>
                    <p className="text-[10px] text-gray-400">{u.email}</p>
                    <span className="text-[9px] font-black text-indigo-500">{u.role}</span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="p-4 bg-indigo-50 rounded-2xl border border-indigo-100">
                <p className="text-lg font-bold text-indigo-900">{currentUser.name}</p>
                <p className="text-sm text-indigo-600">{currentUser.email}</p>
              </div>
            )}
          </section>

          <section className="lg:col-span-2 bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-bold flex items-center"><Package className="text-orange-500 mr-2" /> Cửa hàng sản phẩm</h2>
              <div className="flex gap-2">
                <button onClick={fetchProducts} className="text-gray-400 hover:text-orange-500 p-2"><RefreshCcw size={18} /></button>
                {currentUser.role === 'SELLER' && (
                  <button className="bg-orange-500 text-white px-4 py-2 rounded-xl flex items-center text-sm font-bold hover:bg-orange-600"><Plus size={18} className="mr-1" /> Đăng SP</button>
                )}
              </div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {products.map(p => (
                <div key={p.id} className="group p-5 border border-gray-100 rounded-2xl hover:shadow-xl transition-all bg-white relative overflow-hidden">
                  <div className="absolute top-0 left-0 w-1 h-full bg-orange-400 group-hover:w-2 transition-all"></div>
                  <h3 className="font-extrabold text-gray-900 text-lg group-hover:text-orange-600 transition-colors">{p.name}</h3>
                  <p className="text-2xl font-black text-indigo-600 mt-1">${p.price}</p>
                  <div className="mt-4 flex items-center justify-between">
                    <span className="text-sm font-bold text-gray-400">Tồn kho: {p.stock}</span>
                    <button 
                      disabled={p.stock <= 0 || loading || currentUser.role === 'ADMIN'}
                      onClick={() => createOrder(p.id, p.price)}
                      className={`flex items-center px-6 py-2 rounded-xl text-white font-bold transition-all ${p.stock > 0 && currentUser.role !== 'ADMIN' ? 'bg-indigo-600 hover:bg-indigo-700' : 'bg-gray-300 cursor-not-allowed'}`}
                    >
                      <ShoppingCart size={18} className="mr-2" /> Mua
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

export default App;