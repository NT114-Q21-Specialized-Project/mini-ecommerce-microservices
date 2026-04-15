import React from 'react';
import { ChevronDown } from 'lucide-react';

const sizes = ['36', '37', '38', '39', '40', '41', '42', '43'];
const categories = ['All Categories', 'T-Shirt', 'Accessories', 'Jeans', 'Hoodies'];

const UserSidebar = ({ products, navItems = [], route = '/dashboard' }) => {
  const maxPrice = products.reduce((max, product) => Math.max(max, Number(product.price || 0)), 0);

  return (
    <aside className="fashion-panel rounded-[34px] border p-6">
      <section className="rounded-[24px] border border-slate-100 bg-white p-4 shadow-sm">
        <h3 className="text-lg font-semibold text-slate-900">Workspace</h3>
        <div className="mt-4 space-y-3">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = route === item.path;

            return (
              <a
                key={item.path}
                href={`#${item.path}`}
                className={`side-nav-link flex items-center gap-3 rounded-[20px] px-4 py-3 ${
                  isActive ? 'side-nav-link-active' : ''
                }`}
              >
                <span className="flex h-11 w-11 items-center justify-center rounded-full bg-slate-100/90">
                  <Icon className="h-5 w-5" />
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-base font-semibold">{item.label}</span>
                  <span className="block truncate text-xs text-slate-400">
                    {isActive ? 'Current view' : 'Jump to section'}
                  </span>
                </span>
              </a>
            );
          })}
        </div>
      </section>

      <section className="mt-6">
        <h3 className="text-lg font-semibold text-slate-900">Price Range</h3>
        <div className="mt-4 rounded-[24px] border border-slate-100 bg-white p-4 shadow-sm">
          <div className="flex justify-end">
            <span className="rounded-full bg-slate-950 px-3 py-1 text-[11px] font-semibold text-white">
              ${maxPrice.toFixed(0)}
            </span>
          </div>
          <div className="mt-4 h-24 overflow-hidden rounded-[18px] bg-gradient-to-br from-cyan-50 to-sky-100 p-3">
            <div className="relative h-full">
              <div className="absolute bottom-0 left-0 h-20 w-full rounded-t-[120px] bg-gradient-to-r from-cyan-400 to-sky-500" />
            </div>
          </div>
        </div>
      </section>

      <section className="mt-6 rounded-[24px] border border-slate-100 bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold text-slate-900">Categories</h3>
          <ChevronDown className="h-4 w-4 text-slate-400" />
        </div>
        <div className="mt-4 space-y-3">
          {categories.map((category, index) => (
            <label key={category} className="flex items-center gap-3 text-sm text-slate-500">
              <span
                className={`flex h-4 w-4 items-center justify-center rounded-full border ${
                  index === 0 ? 'border-sky-400 bg-sky-400' : 'border-slate-300 bg-white'
                }`}
              >
                {index === 0 ? <span className="h-1.5 w-1.5 rounded-full bg-white" /> : null}
              </span>
              <span className={index === 0 ? 'font-medium text-sky-500' : ''}>{category}</span>
            </label>
          ))}
        </div>
      </section>

      <section className="mt-6 rounded-[24px] border border-slate-100 bg-white p-4 shadow-sm">
        <h3 className="text-lg font-semibold text-slate-900">Size</h3>
        <div className="mt-4 grid grid-cols-4 gap-2">
          {sizes.map((size) => (
            <button
              key={size}
              type="button"
              className="rounded-[14px] border border-slate-200 bg-white px-0 py-2 text-xs font-medium text-slate-500 transition hover:border-sky-300 hover:text-sky-600"
            >
              {size}
            </button>
          ))}
        </div>
        <button
          type="button"
          className="mt-5 w-full rounded-full bg-sky-500 px-4 py-3 text-sm font-semibold text-white transition hover:bg-sky-600"
        >
          Apply
        </button>
      </section>
    </aside>
  );
};

export default UserSidebar;
