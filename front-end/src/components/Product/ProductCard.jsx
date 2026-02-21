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
    <article className="group rounded-2xl border border-slate-200 bg-white/80 p-4 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
      <div className="mb-2 flex items-start justify-between gap-2">
        <h3 className="text-base font-semibold text-slate-900">{product.name}</h3>
        <span
          className={`rounded-full px-2 py-1 text-xs font-semibold ${
            outOfStock ? 'bg-rose-100 text-rose-700' : 'bg-emerald-100 text-emerald-700'
          }`}
        >
          {outOfStock ? 'Hết hàng' : `Kho: ${product.stock}`}
        </span>
      </div>

      <p className="text-2xl font-extrabold text-slate-900">${Number(product.price || 0).toFixed(2)}</p>

      <div className="mt-4 flex items-center gap-2">
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
          className="w-20 rounded-lg border border-slate-200 px-2 py-2 text-sm outline-none focus:border-cyan-400 focus:ring-2 focus:ring-cyan-200"
          disabled={!canBuy || outOfStock}
        />
        <button
          type="button"
          onClick={handleBuy}
          disabled={disabled}
          className={`inline-flex flex-1 items-center justify-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold transition ${
            disabled
              ? 'cursor-not-allowed bg-slate-200 text-slate-500'
              : 'bg-cyan-600 text-white hover:bg-cyan-700'
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
