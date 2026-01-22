import React from 'react';
import { User, ShieldCheck } from 'lucide-react';

const UserSidebar = ({ currentUser, users }) => {
  const isAdmin = currentUser?.role === 'ADMIN';

  return (
    <section className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
      <h2 className="text-xl font-bold mb-6 flex items-center">
        {isAdmin ? <ShieldCheck className="text-red-500 mr-2" /> : <User className="text-indigo-500 mr-2" />}
        {isAdmin ? 'Quản trị hệ thống' : 'Thông tin cá nhân'}
      </h2>
      
      {isAdmin ? (
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
          <p className="text-lg font-bold text-indigo-900">{currentUser?.name}</p>
          <p className="text-sm text-indigo-600">{currentUser?.email}</p>
        </div>
      )}
    </section>
  );
};

export default UserSidebar;