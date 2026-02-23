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
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/40 p-4 backdrop-blur-sm">
      <div className="glass-panel w-full max-w-md rounded-[32px] border p-6 shadow-2xl md:p-8">
        <div className="mb-5 flex items-center justify-between">
          <h3 className="text-xl font-bold text-slate-900">Đăng sản phẩm mới</h3>
          <button
            type="button"
            onClick={onClose}
            className="rounded-2xl border border-slate-200 p-2 text-slate-500 transition hover:bg-slate-100"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <input
            type="text"
            required
            placeholder="Tên sản phẩm"
            className="w-full rounded-2xl border border-sky-100 bg-white px-4 py-3 outline-none transition focus:border-sky-300 focus:ring-2 focus:ring-sky-200"
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
              className="w-full rounded-2xl border border-sky-100 bg-white px-4 py-3 outline-none transition focus:border-sky-300 focus:ring-2 focus:ring-sky-200"
              value={formData.price}
              onChange={(e) => setFormData({ ...formData, price: e.target.value })}
            />
            <input
              type="number"
              required
              min="0"
              placeholder="Tồn kho"
              className="w-full rounded-2xl border border-sky-100 bg-white px-4 py-3 outline-none transition focus:border-sky-300 focus:ring-2 focus:ring-sky-200"
              value={formData.stock}
              onChange={(e) => setFormData({ ...formData, stock: e.target.value })}
            />
          </div>

          <button
            disabled={loading}
            type="submit"
            className="w-full rounded-2xl bg-sky-600 px-4 py-3 font-semibold text-white transition hover:bg-sky-700 disabled:cursor-not-allowed disabled:bg-slate-400"
          >
            {loading ? 'Đang xử lý...' : 'Xác nhận đăng'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default ProductForm;
