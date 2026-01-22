import React from 'react';
import { ShoppingCart } from 'lucide-react';

const ProductCard = ({ product, onBuy, loading, userRole }) => {
  return (
    <div className="group p-5 border border-gray-100 rounded-2xl hover:shadow-xl transition-all bg-white relative overflow-hidden">
      <div className="absolute top-0 left-0 w-1 h-full bg-orange-400 group-hover:w-2 transition-all"></div>
      <h3 className="font-extrabold text-gray-900 text-lg group-hover:text-orange-600 transition-colors">{product.name}</h3>
      <p className="text-2xl font-black text-indigo-600 mt-1">${product.price}</p>
      <div className="mt-4 flex items-center justify-between">
        <span className="text-sm font-bold text-gray-400">Tồn kho: {product.stock}</span>
        <button 
          disabled={product.stock <= 0 || loading || userRole === 'ADMIN'}
          onClick={() => onBuy(product.id, product.price)}
          className={`flex items-center px-6 py-2 rounded-xl text-white font-bold transition-all ${
            product.stock > 0 && userRole !== 'ADMIN' ? 'bg-indigo-600 hover:bg-indigo-700' : 'bg-gray-300 cursor-not-allowed'
          }`}
        >
          <ShoppingCart size={18} className="mr-2" /> Mua
        </button>
      </div>
    </div>
  );
};

export default ProductCard;