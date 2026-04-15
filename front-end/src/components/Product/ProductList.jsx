import React from 'react';
import { Plus, RotateCw } from 'lucide-react';

import ProductCard from './ProductCard';

const ProductList = ({ products, onRefresh, onBuy, loading, currentUser, onOpenAddModal, compact = false }) => {
  const canCreateProduct = currentUser?.role === 'SELLER' || currentUser?.role === 'ADMIN';

  return (
    <section className="fashion-panel rounded-[30px] border p-5">
      <div className="mb-5 flex items-center justify-between gap-4">
        <div>
          <h2 className="text-[28px] font-bold text-slate-900">New Trend Style</h2>
          <p className="mt-1 text-sm text-slate-500">Một dải sản phẩm nổi bật được bày như mockup mẫu.</p>
        </div>

        <div className="flex items-center gap-2">
          {compact ? (
            <a
              href="#/products"
              className="rounded-full border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-500 transition hover:border-sky-200 hover:text-sky-600"
            >
              View All
            </a>
          ) : (
            <button
              type="button"
              onClick={onRefresh}
              className="rounded-full border border-sky-100 bg-white px-4 py-2 text-xs font-semibold text-sky-700 transition hover:bg-sky-50"
            >
              <span className="inline-flex items-center gap-1">
                <RotateCw className="h-4 w-4" />
                Refresh
              </span>
            </button>
          )}

          {canCreateProduct && !compact ? (
            <button
              type="button"
              onClick={onOpenAddModal}
              className="rounded-full bg-sky-500 px-4 py-2 text-xs font-semibold text-white transition hover:bg-sky-600"
            >
              <span className="inline-flex items-center gap-1">
                <Plus className="h-4 w-4" />
                New
              </span>
            </button>
          ) : null}
        </div>
      </div>

      {products.length === 0 ? (
        <div className="rounded-[24px] border border-dashed border-slate-200 bg-slate-50/80 p-10 text-center text-sm text-slate-500">
          Chưa có sản phẩm nào trong hệ thống.
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
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
