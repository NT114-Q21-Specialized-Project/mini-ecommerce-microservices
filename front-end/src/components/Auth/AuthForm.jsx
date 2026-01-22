import React from 'react';
import { LogIn, UserPlus } from 'lucide-react';

const AuthForm = ({ isLoginView, setIsLoginView, formData, setFormData, handleAuth, loading }) => (
  <div className="max-w-md mx-auto bg-white p-8 rounded-3xl shadow-xl border border-gray-100 mt-10">
    <div className="flex justify-center mb-6">
      <div className="p-3 bg-indigo-100 rounded-full text-indigo-600">
        {isLoginView ? <LogIn size={32} /> : <UserPlus size={32} />}
      </div>
    </div>
    <h2 className="text-2xl font-bold text-center text-gray-800 mb-6">
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

export default AuthForm;