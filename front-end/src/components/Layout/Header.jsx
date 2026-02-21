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
    <header className="glass-panel mb-6 rounded-3xl border p-4 md:p-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="inline-flex items-center gap-2 rounded-full border border-cyan-200 bg-cyan-50 px-3 py-1 text-xs font-semibold uppercase tracking-widest text-cyan-700">
            <ShoppingBag className="h-3.5 w-3.5" />
            Microservices Hub
          </p>
          <h1 className="mt-3 text-3xl font-extrabold leading-tight text-slate-900 md:text-4xl">
            Mini Ecommerce Platform
          </h1>
          <p className="mt-2 text-sm text-slate-600">
            Contract-first API, JWT auth, idempotent order flow, outbox-ready foundation.
          </p>
        </div>

        {currentUser && (
          <div className="rounded-2xl border border-slate-200 bg-white/80 p-3 shadow-sm">
            <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-700">
              <RoleIcon className="h-4 w-4 text-cyan-700" />
              {currentUser.name} ({roleLabel[currentUser.role] || currentUser.role})
            </div>
            <p className="text-xs text-slate-500">{currentUser.email}</p>
            <p className="mt-1 text-xs text-slate-500">
              Token hết hạn: {session?.expiresAt ? new Date(session.expiresAt * 1000).toLocaleString('vi-VN') : '-'}
            </p>
            <button
              type="button"
              onClick={() => onLogout()}
              className="mt-3 inline-flex items-center gap-1 rounded-xl bg-slate-900 px-3 py-2 text-xs font-semibold text-white transition hover:bg-slate-700"
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
