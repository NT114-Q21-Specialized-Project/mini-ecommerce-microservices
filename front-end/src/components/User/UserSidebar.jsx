import React from 'react';
import {
  Boxes,
  CircleDollarSign,
  ShieldCheck,
  Store,
  UserCircle2,
  Users2,
} from 'lucide-react';

const roleTitle = {
  ADMIN: 'Quản trị hệ thống',
  SELLER: 'Nhà bán hàng',
  CUSTOMER: 'Khách hàng',
};

const roleIcon = {
  ADMIN: ShieldCheck,
  SELLER: Store,
  CUSTOMER: UserCircle2,
};

const UserSidebar = ({ currentUser, users, orders, products, navItems, route }) => {
  const RoleIcon = roleIcon[currentUser?.role] || UserCircle2;
  const activeNavItems = navItems.filter((item) => !item.roles || item.roles.includes(currentUser?.role));
  const lowStockCount = products.filter((product) => Number(product.stock || 0) > 0 && Number(product.stock || 0) <= 5).length;
  const catalogValue = products.reduce(
    (sum, product) => sum + Number(product.price || 0) * Math.max(Number(product.stock || 0), 1),
    0
  );

  return (
    <aside className="dashboard-card dashboard-sidebar-surface rounded-[38px] border p-5 lg:sticky lg:top-4 lg:h-[calc(100vh-2rem)] lg:overflow-y-auto">
      <div className="rounded-[30px] bg-slate-950 p-5 text-white shadow-xl shadow-slate-900/10">
        <p className="text-[11px] font-bold uppercase tracking-[0.28em] text-cyan-200">Mini Ecommerce</p>
        <h2 className="mt-3 text-2xl font-bold leading-tight">Fashion microservices control room</h2>
        <p className="mt-3 text-sm text-slate-300">
          Một layout gọn để xem catalog, tồn kho và luồng đơn hàng trong cùng một màn hình.
        </p>
      </div>

      <div className="mt-5 rounded-[30px] border border-slate-200 bg-white/90 p-4">
        <div className="flex items-center gap-3">
          <div className="flex h-14 w-14 items-center justify-center rounded-[22px] bg-gradient-to-br from-cyan-300 via-sky-400 to-blue-700 text-white shadow-lg shadow-cyan-100">
            <RoleIcon className="h-6 w-6" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-lg font-bold text-slate-900">{currentUser?.name}</p>
            <p className="truncate text-sm text-slate-500">{currentUser?.email}</p>
          </div>
        </div>

        <div className="mt-4 flex items-center justify-between rounded-[22px] bg-sky-50 px-4 py-3">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-[0.24em] text-slate-400">Current Role</p>
            <p className="mt-1 text-sm font-semibold text-slate-900">{roleTitle[currentUser?.role] || 'Thông tin user'}</p>
          </div>
          <span className="rounded-full bg-white px-3 py-1 text-xs font-semibold text-sky-700 shadow-sm">
            {currentUser?.role}
          </span>
        </div>
      </div>

      <div className="mt-5">
        <p className="mb-3 text-[11px] font-bold uppercase tracking-[0.28em] text-slate-400">Workspace</p>
        <div className="space-y-2">
          {activeNavItems.map((item) => {
            const Icon = item.icon;
            const isActive = route === item.path;

            return (
              <a
                key={item.path}
                href={`#${item.path}`}
                className={`side-nav-link flex items-center gap-3 rounded-[22px] px-4 py-3 ${
                  isActive ? 'side-nav-link-active' : ''
                }`}
              >
                <span className={`flex h-10 w-10 items-center justify-center rounded-[16px] ${
                  isActive ? 'bg-white text-sky-700' : 'bg-slate-100 text-slate-500'
                }`}>
                  <Icon className="h-4 w-4" />
                </span>
                <div>
                  <p className="text-sm font-semibold">{item.label}</p>
                  <p className="text-xs text-slate-400">{isActive ? 'Current view' : 'Jump to section'}</p>
                </div>
              </a>
            );
          })}
        </div>
      </div>

      <div className="mt-5 grid grid-cols-2 gap-3">
        <div className="rounded-[24px] border border-slate-200 bg-white/85 p-4">
          <p className="text-[10px] font-bold uppercase tracking-[0.24em] text-slate-400">Catalog</p>
          <p className="mt-2 text-2xl font-bold text-slate-900">{products.length}</p>
          <p className="mt-1 text-xs text-slate-500">live products</p>
        </div>
        <div className="rounded-[24px] border border-slate-200 bg-white/85 p-4">
          <p className="text-[10px] font-bold uppercase tracking-[0.24em] text-slate-400">Open Orders</p>
          <p className="mt-2 text-2xl font-bold text-slate-900">{orders.length}</p>
          <p className="mt-1 text-xs text-slate-500">tracked orders</p>
        </div>
        <div className="rounded-[24px] border border-slate-200 bg-white/85 p-4">
          <div className="flex items-center gap-2 text-slate-700">
            <Boxes className="h-4 w-4 text-sky-600" />
            <p className="text-[10px] font-bold uppercase tracking-[0.24em] text-slate-400">Low Stock</p>
          </div>
          <p className="mt-2 text-2xl font-bold text-slate-900">{lowStockCount}</p>
          <p className="mt-1 text-xs text-slate-500">needs attention</p>
        </div>
        <div className="rounded-[24px] border border-slate-200 bg-white/85 p-4">
          <div className="flex items-center gap-2 text-slate-700">
            <CircleDollarSign className="h-4 w-4 text-sky-600" />
            <p className="text-[10px] font-bold uppercase tracking-[0.24em] text-slate-400">Catalog Value</p>
          </div>
          <p className="mt-2 text-2xl font-bold text-slate-900">${catalogValue.toFixed(0)}</p>
          <p className="mt-1 text-xs text-slate-500">approx. inventory value</p>
        </div>
      </div>

      <div className="mt-5 rounded-[30px] border border-slate-200 bg-white/90 p-4">
        <p className="text-[11px] font-bold uppercase tracking-[0.28em] text-slate-400">Artwork Folders</p>
        <div className="mt-4 space-y-3 text-sm text-slate-600">
          <div className="rounded-[22px] bg-slate-50 px-4 py-3">
            <p className="font-semibold text-slate-900">Hero image</p>
            <p className="mt-1 font-mono text-xs text-slate-500">public/dashboard-media/hero/hero-main.png</p>
          </div>
          <div className="rounded-[22px] bg-slate-50 px-4 py-3">
            <p className="font-semibold text-slate-900">Product images</p>
            <p className="mt-1 font-mono text-xs text-slate-500">
              white-cap.png, grey-tee.png, denim-shirt.png, white-sneaker.png
            </p>
          </div>
        </div>
      </div>

      {currentUser?.role === 'ADMIN' && (
        <div className="mt-5 rounded-[30px] border border-slate-200 bg-white/90 p-4">
          <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-700">
            <Users2 className="h-4 w-4 text-sky-700" />
            User đang hoạt động ({users.length})
          </div>
          <div className="max-h-52 space-y-2 overflow-y-auto pr-1">
            {users.map((user) => (
              <div key={user.id} className="rounded-[20px] border border-slate-200 bg-slate-50/90 p-3">
                <p className="truncate text-sm font-semibold text-slate-800">{user.name}</p>
                <p className="truncate text-xs text-slate-500">{user.email}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </aside>
  );
};

export default UserSidebar;
