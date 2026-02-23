import React from 'react';
import { Package2, Plus, RotateCw } from 'lucide-react';
import ProductCard from './ProductCard';

const ProductList = ({ products, onRefresh, onBuy, loading, currentUser, onOpenAddModal }) => {
  const canCreateProduct = currentUser?.role === 'SELLER' || currentUser?.role === 'ADMIN';

  return (
    <section className="glass-panel rounded-[32px] border p-5 md:p-6">
      <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900 md:text-xl">
          <Package2 className="h-5 w-5 text-sky-600" />
          Product Catalog
        </h2>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={onRefresh}
            className="inline-flex items-center gap-1 rounded-2xl border border-sky-200 bg-white px-3 py-2 text-xs font-semibold text-sky-700 transition hover:bg-sky-50"
            title="Làm mới danh sách sản phẩm"
          >
            <RotateCw className="h-4 w-4" />
            Refresh
          </button>

          {canCreateProduct && (
            <button
              type="button"
              onClick={onOpenAddModal}
              className="inline-flex items-center gap-1 rounded-2xl bg-sky-600 px-3 py-2 text-xs font-semibold text-white transition hover:bg-sky-700"
            >
              <Plus className="h-4 w-4" />
              Đăng sản phẩm
            </button>
          )}
        </div>
      </div>

      {products.length === 0 ? (
        <div className="rounded-3xl border border-dashed border-slate-200 bg-white/70 p-8 text-center text-sm text-slate-500">
          Chưa có sản phẩm nào trong hệ thống.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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
