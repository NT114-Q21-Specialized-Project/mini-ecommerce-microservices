import React, { useMemo } from 'react';
import { Plus } from 'lucide-react';

import MediaSlot from '../Layout/MediaSlot';
import { resolveProductMedia } from './productMedia';

const ProductCard = ({ product, onBuy, loading, userRole }) => {
  const canBuy = userRole === 'CUSTOMER' || userRole === 'ADMIN';
  const disabled = loading || !canBuy || product.isShowcase || !product.id || Number(product.stock || 0) <= 0;
  const mediaMeta = useMemo(() => resolveProductMedia(product), [product]);
  const comparePrice = Number(product.price || 0) * 1.1;

  return (
    <article className="trend-card rounded-[26px] border p-3">
      <div className="trend-card-art rounded-[20px] px-3 py-3">
        <div className="flex items-start justify-between gap-2">
          <span className="rounded-full bg-sky-500 px-2.5 py-1 text-[10px] font-bold text-white shadow-sm">
            {mediaMeta.badge}
          </span>
          <span className="text-[10px] font-semibold uppercase tracking-[0.2em] text-slate-500">
            {Number(product.stock || 0) > 0 ? 'In stock' : 'Sold out'}
          </span>
        </div>

        <MediaSlot
          src={mediaMeta.src}
          alt={product.name}
          title={product.name || 'Product artwork'}
          hint={`Replace ${mediaMeta.fileLabel}`}
          className="mt-3 h-36 rounded-[18px] bg-transparent"
          imgClassName="h-full w-full object-contain"
        />
      </div>

      <div className="px-1 pb-1 pt-3">
        <p className="truncate text-sm font-semibold text-slate-900">{product.name}</p>
        <p className="mt-1 text-[11px] text-slate-400">Sport families</p>

        <div className="mt-3 flex items-center justify-between gap-3">
          <div>
            <p className="text-[11px] text-slate-400 line-through">${comparePrice.toFixed(2)}</p>
            <p className="text-base font-bold text-slate-900">${Number(product.price || 0).toFixed(2)}</p>
          </div>

          <button
            type="button"
            onClick={() => !disabled && onBuy(product.id, 1)}
            disabled={disabled}
            className={`inline-flex h-10 w-10 items-center justify-center rounded-full transition ${
              disabled
                ? 'cursor-not-allowed bg-slate-200 text-slate-500'
                : 'bg-gradient-to-r from-sky-500 to-blue-600 text-white shadow-lg shadow-sky-200 hover:-translate-y-0.5'
            }`}
            aria-label={`Buy ${product.name}`}
          >
            <Plus className="h-4 w-4" />
          </button>
        </div>
      </div>
    </article>
  );
};

export default ProductCard;
