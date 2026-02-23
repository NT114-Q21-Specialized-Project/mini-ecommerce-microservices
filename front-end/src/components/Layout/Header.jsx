import React from 'react';
import { LogOut, ShieldCheck, ShoppingBag, Store, UserCircle2 } from 'lucide-react';

const roleLabel = {
  ADMIN: 'Admin',
  SELLER: 'Seller',
  CUSTOMER: 'Customer',
};

const roleIcon = {
  ADMIN: ShieldCheck,
  SELLER: Store,
  CUSTOMER: UserCircle2,
};

const Header = ({ currentUser, session, onLogout }) => {
  const RoleIcon = roleIcon[currentUser?.role] || UserCircle2;

  return (
    <header className="glass-panel mb-6 rounded-[32px] border px-6 py-5">
      <div className="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="inline-flex items-center gap-2 rounded-full border border-sky-200 bg-white px-3 py-1 text-xs font-semibold uppercase tracking-[0.3em] text-sky-600">
            <ShoppingBag className="h-3.5 w-3.5" />
            Mini Ecommerce
          </p>
          <h1 className="mt-3 text-3xl font-bold text-slate-900 md:text-4xl">
            Cold Blue Commerce Studio
          </h1>
          <p className="mt-2 text-sm text-slate-500">
            Quản trị hệ thống, sản phẩm và đơn hàng trong một dashboard thống nhất.
          </p>
        </div>

        {currentUser && (
          <div className="rounded-3xl border border-slate-200 bg-white/80 p-4 shadow-sm">
            <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-700">
              <RoleIcon className="h-4 w-4 text-sky-700" />
              {currentUser.name} ({roleLabel[currentUser.role] || currentUser.role})
            </div>
            <p className="text-xs text-slate-500">{currentUser.email}</p>
            <p className="mt-1 text-xs text-slate-500">
              Token hết hạn: {session?.expiresAt ? new Date(session.expiresAt * 1000).toLocaleString('vi-VN') : '-'}
            </p>
            <button
              type="button"
              onClick={() => onLogout()}
              className="mt-3 inline-flex items-center gap-1 rounded-2xl bg-slate-900 px-3 py-2 text-xs font-semibold text-white transition hover:bg-slate-700"
            >
              <LogOut className="h-4 w-4" />
              Đăng xuất
            </button>
          </div>
        )}
      </div>
    </header>
  );
};

export default Header;
