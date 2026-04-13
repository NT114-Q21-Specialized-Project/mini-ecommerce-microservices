import React, { useMemo, useState } from 'react';
import { ShoppingCart } from 'lucide-react';

import MediaSlot from '../Layout/MediaSlot';

const normalizeText = (value) =>
  (value || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');

const toMediaSlug = (value) =>
  normalizeText(value || 'product')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'product';

const mediaCatalog = [
  { file: 'white-cap.png', keywords: ['hat', 'cap', 'horee'] },
  { file: 'denim-shirt.png', keywords: ['denim', 'emporia', 'armoni', 'button'] },
  { file: 'grey-tee.png', keywords: ['v-neck', 'v neck', 'tshirt', 't-shirt', 'tee'] },
  { file: 'white-sneaker.png', keywords: ['shoe', 'sneaker', 'adisia', 'speed'] },
];

const resolveProductMedia = (product) => {
  if (product.imageUrl) {
    return {
      src: product.imageUrl,
      fileLabel: product.imageUrl,
    };
  }

  const normalizedName = normalizeText(product.name);
  const matchedMedia = mediaCatalog.find((entry) =>
    entry.keywords.some((keyword) => normalizedName.includes(keyword))
  );

  if (matchedMedia) {
    return {
      src: `/dashboard-media/products/${matchedMedia.file}`,
      fileLabel: matchedMedia.file,
    };
  }

  const mediaSlug = toMediaSlug(product.name);
  return {
    src: `/dashboard-media/products/${mediaSlug}.png`,
    fileLabel: `${mediaSlug}.png`,
  };
};

const ProductCard = ({ product, onBuy, loading, userRole }) => {
  const [quantity, setQuantity] = useState(1);

  const canBuy = userRole === 'CUSTOMER' || userRole === 'ADMIN';
  const stock = Number(product.stock || 0);
  const outOfStock = stock <= 0;
  const disabled = loading || !canBuy || outOfStock;
  const mediaMeta = useMemo(() => resolveProductMedia(product), [product]);

  const handleBuy = () => {
    onBuy(product.id, quantity);
  };

  return (
    <article className="dashboard-product-card group rounded-[34px] border border-white/70 p-3">
      <div className="rounded-[28px] bg-gradient-to-br from-cyan-100/95 via-sky-100/80 to-blue-100/80 p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <span className="inline-flex rounded-full bg-white/80 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.22em] text-sky-700">
              {outOfStock ? 'Restock' : stock <= 5 ? 'Low Stock' : 'Ready To Ship'}
            </span>
            <p className="mt-3 text-xs font-medium uppercase tracking-[0.2em] text-slate-500">Drop Asset</p>
          </div>

          <span className="rounded-full bg-slate-950 px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.2em] text-white">
            SKU {product.id?.slice(0, 6) ?? 'NA'}
          </span>
        </div>

        <MediaSlot
          src={mediaMeta.src}
          alt={product.name}
          title={product.name || 'Product Artwork'}
          hint={`Replace ${mediaMeta.fileLabel} inside public/dashboard-media/products if needed`}
          className="mt-4 h-44 rounded-[28px] border border-white/70 bg-white/65 p-2 shadow-inner"
          imgClassName="h-full w-full rounded-[22px] object-cover"
        />
      </div>

      <div className="px-2 pb-2 pt-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-base font-bold text-slate-900">{product.name}</h3>
            <p className="mt-1 text-sm text-slate-500">
              {outOfStock ? 'Tạm hết hàng trong kho.' : `Còn ${stock} sản phẩm sẵn sàng để xử lý.`}
            </p>
          </div>
          <div className="rounded-[22px] bg-slate-950 px-3 py-2 text-right text-white shadow-lg shadow-slate-900/10">
            <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-white/65">Price</p>
            <p className="mt-1 text-lg font-bold">${Number(product.price || 0).toFixed(2)}</p>
          </div>
        </div>

        <div className="mt-5 flex items-center gap-2">
          <input
            type="number"
            min="1"
            max={Math.max(stock || 1, 1)}
            value={quantity}
            onChange={(e) => {
              const nextValue = Number(e.target.value);
              if (Number.isNaN(nextValue)) {
                return;
              }
              setQuantity(Math.min(Math.max(1, nextValue), Math.max(stock || 1, 1)));
            }}
            className="dashboard-input w-20 rounded-[18px] px-3 py-3 text-center text-sm font-semibold text-slate-700 outline-none"
            disabled={!canBuy || outOfStock}
          />
          <button
            type="button"
            onClick={handleBuy}
            disabled={disabled}
            className={`inline-flex flex-1 items-center justify-center gap-2 rounded-[20px] px-4 py-3 text-sm font-semibold transition ${
              disabled
                ? 'cursor-not-allowed bg-slate-200 text-slate-500'
                : 'bg-gradient-to-r from-sky-600 via-cyan-500 to-blue-700 text-white shadow-lg shadow-cyan-200/70 hover:-translate-y-0.5'
            }`}
          >
            <ShoppingCart className="h-4 w-4" />
            {canBuy ? 'Add To Order' : 'View Only'}
          </button>
        </div>
      </div>
    </article>
  );
};

export default ProductCard;
