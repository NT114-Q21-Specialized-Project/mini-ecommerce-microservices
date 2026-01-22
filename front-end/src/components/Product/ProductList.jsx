import React from 'react';
import { Package, RefreshCcw, Plus } from 'lucide-react';
import ProductCard from './ProductCard';

const ProductList = ({ products, onRefresh, onBuy, loading, currentUser }) => {
  return (
    <section className="lg:col-span-2 bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-bold flex items-center">
          <Package className="text-orange-500 mr-2" /> Cửa hàng sản phẩm
        </h2>
        <div className="flex gap-2">
          <button onClick={onRefresh} className="text-gray-400 hover:text-orange-500 p-2">
            <RefreshCcw size={18} />
          </button>
          {currentUser?.role === 'SELLER' && (
            <button className="bg-orange-500 text-white px-4 py-2 rounded-xl flex items-center text-sm font-bold hover:bg-orange-600">
              <Plus size={18} className="mr-1" /> Đăng SP
            </button>
          )}
        </div>
      </div>
      
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
    </section>
  );
};

export default ProductList;