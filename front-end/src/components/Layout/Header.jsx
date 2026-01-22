import React from 'react';
import { LogOut } from 'lucide-react';

const Header = ({ currentUser, onLogout }) => {
  return (
    <header className="mb-10 flex flex-col items-center">
      <h1 className="text-4xl font-extrabold text-indigo-600 tracking-tight">Mini Ecommerce</h1>
      {currentUser && (
        <div className="mt-4 flex items-center bg-white px-4 py-2 rounded-full shadow-sm border border-gray-100">
          <div className={`w-2 h-2 rounded-full mr-2 ${currentUser.role === 'ADMIN' ? 'bg-red-500' : 'bg-green-500'}`}></div>
          <span className="text-sm font-medium text-gray-700 mr-4">
            {currentUser.name} ({currentUser.role})
          </span>
          <button onClick={onLogout} className="text-gray-400 hover:text-red-500 transition-colors">
            <LogOut size={16} />
          </button>
        </div>
      )}
    </header>
  );
};

export default Header;