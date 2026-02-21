import React from 'react';
import { Ban, Clock3, ListOrdered, RotateCw } from 'lucide-react';

const formatDate = (value) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return date.toLocaleString('vi-VN');
};

const OrderPanel = ({ orders, loading, currentUser, onRefresh, onCancel }) => {
  const canCancel = (order) => {
    if (order.status !== 'CREATED') {
      return false;
    }
    if (currentUser?.role === 'ADMIN') {
      return true;
    }
    return order.userId === currentUser?.id;
  };

  return (
    <section className="glass-panel rounded-3xl border p-5 md:p-6">
      <div className="mb-5 flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900 md:text-xl">
          <ListOrdered className="h-5 w-5 text-cyan-600" />
          Đơn hàng
        </h2>
        <button
          type="button"
          onClick={onRefresh}
          className="inline-flex items-center gap-1 rounded-xl border border-cyan-200 px-3 py-2 text-xs font-semibold text-cyan-700 transition hover:bg-cyan-50"
        >
          <RotateCw className="h-4 w-4" />
          Làm mới
        </button>
      </div>

      {orders.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-white/60 p-8 text-center">
          <Clock3 className="mx-auto mb-3 h-8 w-8 text-slate-300" />
          <p className="text-sm text-slate-500">Chưa có đơn hàng nào.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {orders.map((order) => (
            <article
              key={order.id}
              className="rounded-2xl border border-slate-200 bg-white/80 p-4 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
            >
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                <span className="font-mono text-xs text-slate-500">{order.id?.slice(0, 8)}...</span>
                <span
                  className={`rounded-full px-2 py-1 text-xs font-semibold ${
                    order.status === 'CANCELLED'
                      ? 'bg-rose-100 text-rose-700'
                      : 'bg-emerald-100 text-emerald-700'
                  }`}
                >
                  {order.status}
                </span>
              </div>

              <div className="grid grid-cols-2 gap-2 text-sm text-slate-600 md:grid-cols-4">
                <p>SL: {order.quantity}</p>
                <p>Đơn giá: ${Number(order.unitPrice || 0).toFixed(2)}</p>
                <p>Tổng: ${Number(order.totalAmount || 0).toFixed(2)}</p>
                <p>Tạo lúc: {formatDate(order.createdAt)}</p>
              </div>

              <div className="mt-3 flex items-center justify-end">
                <button
                  type="button"
                  disabled={!canCancel(order) || loading}
                  onClick={() => onCancel(order.id)}
                  className={`inline-flex items-center gap-1 rounded-xl px-3 py-2 text-xs font-semibold transition ${
                    canCancel(order) && !loading
                      ? 'bg-rose-600 text-white hover:bg-rose-700'
                      : 'cursor-not-allowed bg-slate-200 text-slate-500'
                  }`}
                >
                  <Ban className="h-4 w-4" />
                  Hủy đơn
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
};

export default OrderPanel;
