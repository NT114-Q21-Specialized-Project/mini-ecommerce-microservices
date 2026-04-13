import React, { useState } from 'react';
import { X } from 'lucide-react';

const ProductForm = ({ isOpen, onClose, onSubmit, loading }) => {
  const [formData, setFormData] = useState({ name: '', price: '', stock: '' });

  if (!isOpen) {
    return null;
  }

  const handleSubmit = (e) => {
    e.preventDefault();

    const productPayload = {
      name: formData.name.trim(),
      price: Number.parseFloat(formData.price) || 0,
      stock: Number.parseInt(formData.stock, 10) || 0,
    };

    if (!productPayload.name) {
      return;
    }

    onSubmit(productPayload);
    setFormData({ name: '', price: '', stock: '' });
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-md">
      <div className="glass-panel w-full max-w-md rounded-[36px] border p-6 shadow-2xl md:p-8">
        <div className="mb-6 flex items-start justify-between gap-4">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-[0.28em] text-sky-600">Catalog Studio</p>
            <h3 className="mt-2 text-2xl font-bold text-slate-900">Đăng sản phẩm mới</h3>
            <p className="mt-2 text-sm text-slate-500">Tạo thêm một card mới để xuất hiện ngay trong dashboard.</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-[18px] border border-slate-200 p-2 text-slate-500 transition hover:bg-slate-100"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <input
            type="text"
            required
            placeholder="Tên sản phẩm"
            className="dashboard-input w-full rounded-[22px] px-4 py-3 outline-none"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          />

          <div className="grid grid-cols-2 gap-3">
            <input
              type="number"
              required
              min="0.01"
              step="0.01"
              placeholder="Giá"
              className="dashboard-input w-full rounded-[22px] px-4 py-3 outline-none"
              value={formData.price}
              onChange={(e) => setFormData({ ...formData, price: e.target.value })}
            />
            <input
              type="number"
              required
              min="0"
              placeholder="Tồn kho"
              className="dashboard-input w-full rounded-[22px] px-4 py-3 outline-none"
              value={formData.stock}
              onChange={(e) => setFormData({ ...formData, stock: e.target.value })}
            />
          </div>

          <button
            disabled={loading}
            type="submit"
            className="w-full rounded-[24px] bg-slate-950 px-4 py-3 font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
          >
            {loading ? 'Đang xử lý...' : 'Xác nhận đăng'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default ProductForm;
