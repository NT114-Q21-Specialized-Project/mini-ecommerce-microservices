import React from 'react';
import { Fingerprint, LogIn, UserPlus } from 'lucide-react';

const AuthForm = ({ isLoginView, setIsLoginView, formData, setFormData, handleAuth, loading }) => (
  <div className="mx-auto mt-8 w-full max-w-lg">
    <div className="glass-panel rounded-[32px] border p-6 shadow-xl md:p-8">
      <div className="mb-6 flex items-center justify-center">
        <div className="rounded-2xl bg-sky-600 p-3 text-white shadow-lg shadow-sky-200">
          <Fingerprint className="h-7 w-7" />
        </div>
      </div>

      <h2 className="mb-1 text-center text-2xl font-bold text-slate-900">
        {isLoginView ? 'Đăng nhập hệ thống' : 'Tạo tài khoản mới'}
      </h2>
      <p className="mb-6 text-center text-sm text-slate-500">
        {isLoginView
          ? 'Sử dụng JWT token để truy cập toàn bộ microservices'
          : 'Đăng ký và chọn vai trò phù hợp cho workflow của bạn'}
      </p>

      <form onSubmit={handleAuth} className="space-y-4">
        {!isLoginView && (
          <input
            type="text"
            placeholder="Họ và tên"
            required
            value={formData.name}
            className="w-full rounded-2xl border border-sky-100 bg-white px-4 py-3 outline-none transition focus:border-sky-300 focus:ring-2 focus:ring-sky-200"
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          />
        )}

        <input
          type="email"
          placeholder="Email"
          required
          value={formData.email}
          className="w-full rounded-2xl border border-sky-100 bg-white px-4 py-3 outline-none transition focus:border-sky-300 focus:ring-2 focus:ring-sky-200"
          onChange={(e) => setFormData({ ...formData, email: e.target.value })}
        />

        <input
          type="password"
          placeholder="Mật khẩu"
          required
          value={formData.password}
          className="w-full rounded-2xl border border-sky-100 bg-white px-4 py-3 outline-none transition focus:border-sky-300 focus:ring-2 focus:ring-sky-200"
          onChange={(e) => setFormData({ ...formData, password: e.target.value })}
        />

        {!isLoginView && (
          <select
            value={formData.role}
            className="w-full rounded-2xl border border-sky-100 bg-white px-4 py-3 outline-none transition focus:border-sky-300 focus:ring-2 focus:ring-sky-200"
            onChange={(e) => setFormData({ ...formData, role: e.target.value })}
          >
            <option value="CUSTOMER">Customer</option>
            <option value="SELLER">Seller</option>
          </select>
        )}

        <button
          disabled={loading}
          type="submit"
          className="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-sky-600 px-4 py-3 font-semibold text-white transition hover:bg-sky-700 disabled:cursor-not-allowed disabled:bg-slate-400"
        >
          {isLoginView ? <LogIn className="h-4 w-4" /> : <UserPlus className="h-4 w-4" />}
          {loading ? 'Đang xử lý...' : isLoginView ? 'Đăng nhập' : 'Đăng ký'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-slate-500">
        {isLoginView ? 'Chưa có tài khoản?' : 'Đã có tài khoản?'}
        <button
          type="button"
          onClick={() => setIsLoginView(!isLoginView)}
          className="ml-2 font-semibold text-sky-700 hover:text-sky-500"
        >
          {isLoginView ? 'Đăng ký ngay' : 'Đăng nhập'}
        </button>
      </p>
    </div>
  </div>
);

export default AuthForm;
