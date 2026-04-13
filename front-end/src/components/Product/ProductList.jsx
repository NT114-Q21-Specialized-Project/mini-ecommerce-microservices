import React from 'react';
import { Package2, Plus, RotateCw } from 'lucide-react';

import ProductCard from './ProductCard';

const ProductList = ({
  products,
  onRefresh,
  onBuy,
  loading,
  currentUser,
  onOpenAddModal,
  title = 'Product Catalog',
  subtitle = 'All available items in the store.',
}) => {
  const canCreateProduct = currentUser?.role === 'SELLER' || currentUser?.role === 'ADMIN';

  return (
    <section className="dashboard-card rounded-[38px] border p-5 md:p-6">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.3em] text-sky-600">Trend Selection</p>
          <h2 className="mt-2 flex items-center gap-2 text-2xl font-bold text-slate-900">
            <Package2 className="h-5 w-5 text-sky-600" />
            {title}
          </h2>
          <p className="mt-2 max-w-2xl text-sm text-slate-500">{subtitle}</p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={onRefresh}
            className="chip-soft inline-flex items-center gap-1 rounded-full px-4 py-3 text-xs font-semibold text-sky-700 transition hover:bg-sky-50"
            title="Làm mới danh sách sản phẩm"
          >
            <RotateCw className="h-4 w-4" />
            Refresh
          </button>

          {canCreateProduct && (
            <button
              type="button"
              onClick={onOpenAddModal}
              className="inline-flex items-center gap-1 rounded-full bg-slate-950 px-4 py-3 text-xs font-semibold text-white transition hover:bg-slate-800"
            >
              <Plus className="h-4 w-4" />
              New Product
            </button>
          )}
        </div>
      </div>

      {products.length === 0 ? (
        <div className="rounded-[30px] border border-dashed border-sky-200 bg-white/70 p-10 text-center text-sm text-slate-500">
          Chưa có sản phẩm nào trong hệ thống.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
          {products.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              onBuy={onBuy}
              loading={loading}
              userRole={currentUser?.role}
            />
          ))}
        </div>
      )}
    </section>
  );
};

export default ProductList;
