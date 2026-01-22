import React, { useState } from 'react';
import { X } from 'lucide-react';

const ProductForm = ({ isOpen, onClose, onSubmit, loading }) => {
  const [formData, setFormData] = useState({ name: '', price: '', stock: '' });

  if (!isOpen) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    
    // Gửi payload sạch, ép kiểu số chính xác để tránh lỗi 400 Bad Request
    const productPayload = {
      name: formData.name.trim(),
      price: parseFloat(formData.price) || 0,
      stock: parseInt(formData.stock, 10) || 0
    };

    if (!productPayload.name) return;

    onSubmit(productPayload);
    
    // Reset form và đóng modal
    setFormData({ name: '', price: '', stock: '' });
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[100] p-4">
      <div className="bg-white rounded-3xl p-8 w-full max-w-md shadow-2xl relative">
        <button onClick={onClose} className="absolute top-4 right-4 text-gray-400 hover:text-gray-600">
          <X size={24} />
        </button>
        
        <h2 className="text-2xl font-bold text-gray-800 mb-6 text-center">Đăng sản phẩm mới</h2>
        
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Tên sản phẩm</label>
            <input
              type="text" required
              className="w-full p-3 rounded-xl border border-gray-200 focus:ring-2 focus:ring-orange-500 outline-none"
              value={formData.name}
              onChange={e => setFormData({...formData, name: e.target.value})}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Giá ($)</label>
              <input
                type="number" required min="0" step="0.01"
                className="w-full p-3 rounded-xl border border-gray-200 focus:ring-2 focus:ring-orange-500 outline-none"
                value={formData.price}
                onChange={e => setFormData({...formData, price: e.target.value})}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Số lượng</label>
              <input
                type="number" required min="1"
                className="w-full p-3 rounded-xl border border-gray-200 focus:ring-2 focus:ring-orange-500 outline-none"
                value={formData.stock}
                onChange={e => setFormData({...formData, stock: e.target.value})}
              />
            </div>
          </div>
          
          <button
            disabled={loading}
            type="submit"
            className="w-full bg-orange-500 text-white p-3 rounded-xl font-bold hover:bg-orange-600 transition-all shadow-lg mt-4 active:scale-95"
          >
            {loading ? 'Đang xử lý...' : 'Xác nhận đăng'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default ProductForm;