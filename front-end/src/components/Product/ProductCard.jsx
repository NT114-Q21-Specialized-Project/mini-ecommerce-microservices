import React, { useState } from 'react';
import { ShoppingCart } from 'lucide-react';

const ProductCard = ({ product, onBuy, loading, userRole }) => {
  const [quantity, setQuantity] = useState(1);

  const canBuy = userRole === 'CUSTOMER' || userRole === 'ADMIN';
  const outOfStock = product.stock <= 0;
  const disabled = loading || !canBuy || outOfStock;

  const handleBuy = () => {
    onBuy(product.id, quantity);
  };

  return (
    <article className="group relative overflow-hidden rounded-[28px] border border-slate-200 bg-white/85 p-4 shadow-sm transition hover:-translate-y-1 hover:shadow-lg">
      <div className="absolute -right-8 -top-8 h-24 w-24 rounded-full bg-sky-100/80 blur-2xl" />
      <div className="relative flex items-start justify-between gap-2">
        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-500 to-sky-200 text-lg font-bold text-white shadow-md shadow-sky-200">
            {(product.name || '?').slice(0, 1).toUpperCase()}
          </div>
          <div>
            <h3 className="text-base font-semibold text-slate-900">{product.name}</h3>
            <p className="text-xs text-slate-400">SKU #{product.id?.slice(0, 6) ?? 'NA'}</p>
          </div>
        </div>
        <span
          className={`rounded-full px-2 py-1 text-xs font-semibold ${
            outOfStock ? 'bg-rose-100 text-rose-700' : 'bg-emerald-100 text-emerald-700'
          }`}
        >
          {outOfStock ? 'Hết hàng' : `Kho: ${product.stock}`}
        </span>
      </div>

      <div className="relative mt-4 flex items-end justify-between">
        <p className="text-2xl font-extrabold text-slate-900">${Number(product.price || 0).toFixed(2)}</p>
        <span className="rounded-full bg-sky-50 px-3 py-1 text-xs font-semibold text-sky-700">Cold blue deal</span>
      </div>

      <div className="relative mt-4 flex items-center gap-2">
        <input
          type="number"
          min="1"
          max={Math.max(product.stock || 1, 1)}
          value={quantity}
          onChange={(e) => {
            const nextValue = Number(e.target.value);
            if (Number.isNaN(nextValue)) {
              return;
            }
            setQuantity(Math.min(Math.max(1, nextValue), Math.max(product.stock || 1, 1)));
          }}
          className="w-20 rounded-2xl border border-sky-100 bg-white px-3 py-2 text-sm outline-none focus:border-sky-300 focus:ring-2 focus:ring-sky-200"
          disabled={!canBuy || outOfStock}
        />
        <button
          type="button"
          onClick={handleBuy}
          disabled={disabled}
          className={`inline-flex flex-1 items-center justify-center gap-2 rounded-2xl px-4 py-2 text-sm font-semibold transition ${
            disabled
              ? 'cursor-not-allowed bg-slate-200 text-slate-500'
              : 'bg-sky-600 text-white hover:bg-sky-700'
          }`}
        >
          <ShoppingCart className="h-4 w-4" />
          Mua ngay
        </button>
      </div>
    </article>
  );
};

export default ProductCard;
