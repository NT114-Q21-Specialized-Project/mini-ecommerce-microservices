import React from 'react';
import { Package, RefreshCcw, Plus } from 'lucide-react';
import ProductCard from './ProductCard';

const ProductList = ({ products, onRefresh, onBuy, loading, currentUser, onOpenAddModal }) => {
  return (
    <section className="lg:col-span-2 bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-bold flex items-center">
          <Package className="text-orange-500 mr-2" /> Cửa hàng sản phẩm
        </h2>
        <div className="flex gap-2">
          <button 
            onClick={onRefresh} 
            className="text-gray-400 hover:text-orange-500 p-2 transition-colors"
            title="Làm mới danh sách"
          >
            <RefreshCcw size={18} />
          </button>
          
          {currentUser?.role === 'SELLER' && (
            <button 
              onClick={onOpenAddModal} // Gọi hàm mở Modal từ App.jsx
              className="bg-orange-500 text-white px-4 py-2 rounded-xl flex items-center text-sm font-bold hover:bg-orange-600 transition-all active:scale-95 shadow-md shadow-orange-100"
            >
              <Plus size={18} className="mr-1" /> Đăng SP
            </button>
          )}
        </div>
      </div>
      
      {products.length === 0 ? (
        <div className="text-center py-20 text-gray-400">
          <Package size={48} className="mx-auto mb-4 opacity-20" />
          <p>Chưa có sản phẩm nào được đăng bán.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {products.map(p => (
            <ProductCard 
              key={p.id} 
              product={p} 
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