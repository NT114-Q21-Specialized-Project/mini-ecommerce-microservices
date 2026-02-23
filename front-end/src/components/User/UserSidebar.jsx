import React from 'react';
import { ShieldCheck, Store, UserCircle2, Users2 } from 'lucide-react';

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

const UserSidebar = ({ currentUser, users, orders }) => {
  const RoleIcon = roleIcon[currentUser?.role] || UserCircle2;
  const isAdmin = currentUser?.role === 'ADMIN';

  return (
    <aside className="glass-panel rounded-[32px] border p-5 md:p-6">
      <div className="mb-5 flex items-center gap-2 text-slate-900">
        <RoleIcon className="h-5 w-5 text-sky-700" />
        <h2 className="text-lg font-semibold">{roleTitle[currentUser?.role] || 'Thông tin user'}</h2>
      </div>

      <div className="rounded-3xl border border-slate-200 bg-white/80 p-4">
        <p className="text-xs uppercase tracking-[0.25em] text-slate-400">Current profile</p>
        <p className="mt-2 text-lg font-bold text-slate-900">{currentUser?.name}</p>
        <p className="text-sm text-slate-600">{currentUser?.email}</p>
        <p className="mt-2 inline-block rounded-full bg-sky-100 px-2 py-1 text-xs font-semibold text-sky-700">
          {currentUser?.role}
        </p>
      </div>

      <div className="mt-4 rounded-3xl border border-slate-200 bg-white/80 p-4">
        <p className="text-xs uppercase tracking-[0.25em] text-slate-400">Snapshot</p>
        <p className="mt-2 text-sm text-slate-700">Sản phẩm khả dụng được tải theo thời gian thực.</p>
        <p className="mt-1 text-sm text-slate-700">Tổng đơn hiển thị: {orders?.length || 0}</p>
      </div>

      {isAdmin && (
        <div className="mt-4 rounded-3xl border border-slate-200 bg-white/80 p-4">
          <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-700">
            <Users2 className="h-4 w-4 text-sky-700" />
            User đang hoạt động ({users.length})
          </div>
          <div className="max-h-64 space-y-2 overflow-y-auto pr-1">
            {users.map((u) => (
              <div key={u.id} className="rounded-2xl border border-slate-200 bg-white p-2.5">
                <p className="truncate text-sm font-semibold text-slate-800">{u.name}</p>
                <p className="truncate text-xs text-slate-500">{u.email}</p>
                <span className="mt-1 inline-block rounded-full bg-sky-600 px-2 py-0.5 text-[10px] font-semibold text-white">
                  {u.role}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </aside>
  );
};

export default UserSidebar;
