import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { ShoppingCart, User, Package, Plus, CheckCircle, AlertCircle, RefreshCcw } from 'lucide-react';

const GATEWAY_URL = "http://localhost:9000/api";

function App() {
  const [users, setUsers] = useState([]);
  const [products, setProducts] = useState([]);
  const [currentUser, setCurrentUser] = useState(null);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    refreshData();
  }, []);

  const refreshData = () => {
    fetchUsers();
    fetchProducts();
  };

  const fetchUsers = async () => {
    try {
      const res = await axios.get(`${GATEWAY_URL}/users`);
      setUsers(res.data);
    } catch (err) {
      console.error("Error fetching users", err);
    }
  };

  const fetchProducts = async () => {
    try {
      const res = await axios.get(`${GATEWAY_URL}/products`);
      setProducts(res.data);
    } catch (err) {
      console.error("Error fetching products", err);
    }
  };

  const createOrder = async (productId, price) => {
    if (!currentUser) {
      setMessage({ type: 'error', text: 'Vui lòng chọn một User (Customer) để mua hàng!' });
      return;
    }
    
    setLoading(true);
    try {
      await axios.post(`${GATEWAY_URL}/orders`, null, {
        params: {
          userId: currentUser.id,
          productId: productId,
          quantity: 1,
          totalAmount: price
        }
      });
      setMessage({ type: 'success', text: `Đặt hàng thành công cho ${currentUser.name}!` });
      fetchProducts(); // Cập nhật lại tồn kho sau khi mua
    } catch (err) {
      const errorMsg = err.response?.data || 'Lỗi hệ thống khi đặt hàng';
      setMessage({ type: 'error', text: errorMsg });
    } finally {
      setLoading(false);
      setTimeout(() => setMessage({ type: '', text: '' }), 5000);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 p-4 md:p-8">
      <header className="mb-10 flex flex-col items-center">
        <h1 className="text-4xl font-extrabold text-indigo-600 tracking-tight">Mini Ecommerce</h1>
        <div className="flex items-center mt-2 text-gray-500 uppercase text-xs tracking-widest">
          <span className="px-2 py-1 bg-gray-200 rounded mr-2">Go</span>
          <span className="px-2 py-1 bg-gray-200 rounded mr-2">Spring Boot</span>
          <span className="px-2 py-1 bg-gray-200 rounded">PostgreSQL</span>
        </div>
      </header>

      {message.text && (
        <div className={`fixed top-5 right-5 z-50 p-4 rounded-xl shadow-2xl flex items-center animate-bounce ${message.type === 'success' ? 'bg-green-500 text-white' : 'bg-red-500 text-white'}`}>
          {message.type === 'success' ? <CheckCircle className="mr-2" /> : <AlertCircle className="mr-2" />}
          {message.text}
        </div>
      )}

      <div className="max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* === SECTION: USERS === */}
        <section className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center">
              <User className="text-indigo-500 mr-2" />
              <h2 className="text-xl font-bold">Tài khoản</h2>
            </div>
            <button onClick={fetchUsers} className="text-gray-400 hover:text-indigo-500 transition-colors">
              <RefreshCcw size={18} />
            </button>
          </div>
          
          <div className="space-y-3">
            {users.length === 0 && <p className="text-gray-400 text-sm italic">Đang tải người dùng...</p>}
            {users.map(u => (
              <div 
                key={u.id} 
                onClick={() => setCurrentUser(u)}
                className={`p-4 border-2 rounded-xl cursor-pointer transition-all duration-200 ${currentUser?.id === u.id ? 'border-indigo-500 bg-indigo-50 shadow-md' : 'border-gray-50 hover:border-gray-200 bg-gray-50'}`}
              >
                <div className="flex justify-between items-start">
                  <p className="font-bold text-gray-800">{u.name}</p>
                  <span className={`px-2 py-0.5 rounded text-[10px] font-black uppercase ${u.role === 'SELLER' ? 'bg-purple-200 text-purple-700' : 'bg-blue-200 text-blue-700'}`}>
                    {u.role}
                  </span>
                </div>
                <p className="text-xs text-gray-500 mt-1 truncate">{u.email}</p>
              </div>
            ))}
          </div>
        </section>

        {/* === SECTION: PRODUCTS === */}
        <section className="lg:col-span-2 bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center">
              <Package className="text-orange-500 mr-2" />
              <h2 className="text-xl font-bold">Cửa hàng sản phẩm</h2>
            </div>
            <div className="flex gap-2">
              <button onClick={fetchProducts} className="text-gray-400 hover:text-orange-500 p-2">
                <RefreshCcw size={18} />
              </button>
              {currentUser?.role === 'SELLER' && (
                <button className="bg-orange-500 text-white px-4 py-2 rounded-xl flex items-center text-sm font-bold hover:bg-orange-600 transition-shadow hover:shadow-lg">
                  <Plus size={18} className="mr-1" /> Đăng SP
                </button>
              )}
            </div>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {products.length === 0 && <p className="text-gray-400 text-sm italic">Chưa có sản phẩm nào được đăng.</p>}
            {products.map(p => (
              <div key={p.id} className="group p-5 border border-gray-100 rounded-2xl hover:shadow-xl transition-all duration-300 bg-white relative overflow-hidden">
                <div className="absolute top-0 left-0 w-1 h-full bg-orange-400 group-hover:w-2 transition-all"></div>
                <div className="flex justify-between items-start">
                  <div>
                    <h3 className="font-extrabold text-gray-900 text-lg group-hover:text-orange-600 transition-colors">{p.name}</h3>
                    <p className="text-2xl font-black text-indigo-600 mt-1">${p.price}</p>
                  </div>
                </div>
                
                <div className="mt-4 flex items-center justify-between">
                  <div className="text-sm">
                    <span className="text-gray-400">Tồn kho:</span>
                    <span className={`ml-1 font-bold ${p.stock < 5 ? 'text-red-500' : 'text-gray-700'}`}>{p.stock}</span>
                  </div>
                  <button 
                    disabled={p.stock <= 0 || loading}
                    onClick={() => createOrder(p.id, p.price)}
                    className={`flex items-center px-6 py-2.5 rounded-xl text-white font-bold transform active:scale-95 transition-all ${p.stock > 0 ? 'bg-indigo-600 hover:bg-indigo-700 shadow-lg hover:shadow-indigo-200' : 'bg-gray-300 cursor-not-allowed'}`}
                  >
                    <ShoppingCart size={18} className="mr-2" /> 
                    {loading ? 'Đang mua...' : 'Mua ngay'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </section>

      </div>
    </div>
  );
}

export default App;