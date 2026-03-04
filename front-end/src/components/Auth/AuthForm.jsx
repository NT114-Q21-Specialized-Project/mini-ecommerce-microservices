import React, { useState } from 'react';
import {
  Eye,
  EyeOff,
  Fingerprint,
  KeyRound,
  LogIn,
  Mail,
  ShieldCheck,
  User,
  UserPlus,
} from 'lucide-react';

const AuthForm = ({ isLoginView, setIsLoginView, formData, setFormData, handleAuth, loading }) => {
  const [showPassword, setShowPassword] = useState(false);

  return (
    <div className="mx-auto w-full max-w-[430px]">
      <div className="auth-card rounded-[30px] border p-6 shadow-2xl md:p-8">
        <div className="mb-5 flex items-center justify-center">
          <div className="auth-pill inline-flex items-center gap-2 rounded-full px-3 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-sky-700">
            <Fingerprint className="h-3.5 w-3.5" />
            Mini Ecommerce
          </div>
        </div>

        <h2 className="text-center text-[34px] font-bold leading-[1.05] text-slate-900 md:text-[38px]">
          E-commerce Microservices
        </h2>
        <p className="mt-2 text-center text-sm text-slate-500">
          {isLoginView ? 'Đăng nhập để truy cập toàn bộ microservices' : 'Tạo tài khoản mới cho workflow của bạn'}
        </p>

        <form onSubmit={handleAuth} className="mt-6 space-y-3.5">
          {!isLoginView && (
            <label className="relative block">
              <User className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Họ và tên"
                required
                value={formData.name}
                className="auth-input w-full py-3 pl-11 pr-4 text-sm font-medium outline-none"
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              />
            </label>
          )}

          <label className="relative block">
            <Mail className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              type="email"
              placeholder="Email"
              required
              value={formData.email}
              className="auth-input w-full py-3 pl-11 pr-4 text-sm font-medium outline-none"
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
            />
          </label>

          <label className="relative block">
            <KeyRound className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              type={showPassword ? 'text' : 'password'}
              placeholder="Mật khẩu"
              required
              value={formData.password}
              className="auth-input w-full py-3 pl-11 pr-11 text-sm font-medium outline-none"
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
            />
            <button
              type="button"
              className="absolute right-3 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-slate-400 transition hover:text-slate-600"
              onClick={() => setShowPassword((prev) => !prev)}
              aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            >
              {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </label>

          {!isLoginView && (
            <label className="relative block">
              <ShieldCheck className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <select
                value={formData.role}
                className="auth-input w-full appearance-none py-3 pl-11 pr-4 text-sm font-medium outline-none"
                onChange={(e) => setFormData({ ...formData, role: e.target.value })}
              >
                <option value="CUSTOMER">Customer</option>
                <option value="SELLER">Seller</option>
              </select>
            </label>
          )}

          {isLoginView && (
            <div className="flex items-center justify-between px-1 pt-1 text-xs text-slate-500">
              <label className="inline-flex items-center gap-2">
                <input type="checkbox" className="h-3.5 w-3.5 rounded border-slate-300 text-sky-600" />
                Remember me
              </label>
              <button type="button" className="font-semibold text-slate-500 transition hover:text-sky-700">
                Forgot password
              </button>
            </div>
          )}

          <button
            disabled={loading}
            type="submit"
          className="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-sky-600 to-blue-700 px-4 py-3 font-semibold text-white transition hover:from-sky-700 hover:to-blue-800 disabled:cursor-not-allowed disabled:from-slate-400 disabled:to-slate-500"
        >
          {isLoginView ? <LogIn className="h-4 w-4" /> : <UserPlus className="h-4 w-4" />}
          {loading ? 'Đang xử lý...' : isLoginView ? 'Đăng nhập' : 'Tạo tài khoản'}
        </button>
        </form>

        <p className="mt-5 text-center text-sm text-slate-500">
          {isLoginView ? 'Chưa có tài khoản?' : 'Đã có tài khoản?'}
          <button
            type="button"
            onClick={() => setIsLoginView(!isLoginView)}
            className="ml-1.5 font-semibold text-sky-700 hover:text-sky-500"
          >
            {isLoginView ? 'Đăng ký ngay' : 'Đăng nhập'}
          </button>
        </p>
      </div>
    </div>
  );
};

export default AuthForm;
