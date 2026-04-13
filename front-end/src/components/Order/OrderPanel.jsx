import React from 'react';
import { Ban, Clock3, CreditCard, ListOrdered, RotateCw } from 'lucide-react';

const formatDate = (value) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return date.toLocaleString('vi-VN');
};

const OrderPanel = ({ orders, loading, currentUser, onRefresh, onCancel, variant = 'full' }) => {
  const canCancel = (order) => {
    if (['CANCELLED', 'FAILED'].includes(order.status)) {
      return false;
    }
    if (currentUser?.role === 'ADMIN') {
      return true;
    }
    return order.userId === currentUser?.id;
  };

  const totalAmount = orders.reduce((sum, order) => sum + Number(order.totalAmount || 0), 0);
  const activeOrders = orders.filter((order) => !['CANCELLED', 'FAILED'].includes(order.status)).length;
  const recentOrders = variant === 'sidebar' ? orders.slice(0, 4) : orders;
  const latestOrder = orders[0];

  return (
    <section className="dashboard-card rounded-[38px] border p-5 md:p-6">
      <div className="mb-5 flex items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.3em] text-sky-600">Order Information</p>
          <h2 className="mt-2 flex items-center gap-2 text-xl font-bold text-slate-900 md:text-2xl">
            <ListOrdered className="h-5 w-5 text-sky-600" />
            {variant === 'sidebar' ? 'Order Pulse' : 'Order Stream'}
          </h2>
        </div>

        <button
          type="button"
          onClick={onRefresh}
          className="chip-soft inline-flex items-center gap-1 rounded-full px-4 py-3 text-xs font-semibold text-sky-700 transition hover:bg-sky-50"
        >
          <RotateCw className="h-4 w-4" />
          Refresh
        </button>
      </div>

      <div className="rounded-[32px] bg-gradient-to-br from-cyan-400 via-sky-500 to-blue-700 p-5 text-white shadow-xl shadow-sky-200/70">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-[0.32em] text-white/70">Payment Rail</p>
            <p className="mt-4 text-2xl font-bold">${totalAmount.toFixed(2)}</p>
            <p className="mt-1 text-sm text-white/75">{orders.length} order đang được theo dõi</p>
          </div>
          <span className="rounded-full bg-white/15 px-3 py-1 text-xs font-semibold text-white/85">
            {activeOrders} active
          </span>
        </div>

        <div className="mt-8 grid grid-cols-2 gap-3 text-sm">
          <div>
            <p className="text-white/60">Card Holder</p>
            <p className="mt-1 font-semibold">{currentUser?.name || 'Guest User'}</p>
          </div>
          <div className="text-right">
            <p className="text-white/60">Latest Order</p>
            <p className="mt-1 font-semibold">{latestOrder?.id?.slice(0, 8) || 'No orders'}</p>
          </div>
        </div>
      </div>

      <div className="mt-5 grid grid-cols-2 gap-3">
        <div className="rounded-[26px] border border-slate-200 bg-white/85 p-4">
          <p className="text-[10px] font-bold uppercase tracking-[0.24em] text-slate-400">Open Flow</p>
          <p className="mt-2 text-2xl font-bold text-slate-900">{activeOrders}</p>
        </div>
        <div className="rounded-[26px] border border-slate-200 bg-white/85 p-4">
          <p className="text-[10px] font-bold uppercase tracking-[0.24em] text-slate-400">Recent Total</p>
          <p className="mt-2 text-2xl font-bold text-slate-900">${totalAmount.toFixed(0)}</p>
        </div>
      </div>

      <div className="mt-5 rounded-[30px] border border-slate-200 bg-white/85 p-4">
        <p className="mb-4 flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.28em] text-slate-400">
          <CreditCard className="h-4 w-4 text-sky-600" />
          Payment Modes
        </p>
        <div className="space-y-3">
          {[
            ['Credit Card', 'Default checkout flow'],
            ['Bank Transfer', 'Manual confirmation'],
            ['Cash On Delivery', 'Fallback settlement'],
          ].map(([title, subtitle], index) => (
            <div key={title} className="flex items-center justify-between rounded-[22px] border border-slate-200 bg-slate-50/80 px-4 py-3">
              <div>
                <p className="text-sm font-semibold text-slate-900">{title}</p>
                <p className="text-xs text-slate-500">{subtitle}</p>
              </div>
              <span
                className={`h-3.5 w-3.5 rounded-full ${
                  index === 0 ? 'bg-sky-500 shadow-lg shadow-sky-300' : 'bg-slate-300'
                }`}
              />
            </div>
          ))}
        </div>
      </div>

      {recentOrders.length === 0 ? (
        <div className="mt-5 rounded-[30px] border border-dashed border-slate-200 bg-white/60 p-8 text-center">
          <Clock3 className="mx-auto mb-3 h-8 w-8 text-slate-300" />
          <p className="text-sm text-slate-500">Chưa có đơn hàng nào.</p>
        </div>
      ) : (
        <div className="mt-5 space-y-3">
          {recentOrders.map((order) => (
            <article key={order.id} className="rounded-[28px] border border-slate-200 bg-white/88 p-4 shadow-sm">
              <div className="mb-3 flex items-center justify-between gap-2">
                <div>
                  <p className="text-[10px] font-bold uppercase tracking-[0.24em] text-slate-400">Order Id</p>
                  <p className="mt-1 font-mono text-xs text-slate-600">{order.id?.slice(0, 8)}...</p>
                </div>
                <span
                  className={`rounded-full px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.18em] ${
                    order.status === 'CONFIRMED'
                      ? 'bg-emerald-100 text-emerald-700'
                      : order.status === 'FAILED'
                        ? 'bg-rose-100 text-rose-700'
                        : order.status === 'CANCELLED'
                          ? 'bg-slate-200 text-slate-700'
                          : 'bg-amber-100 text-amber-700'
                  }`}
                >
                  {order.status}
                </span>
              </div>

              <div className="grid grid-cols-2 gap-2 text-sm text-slate-600">
                <p>SL: {order.quantity}</p>
                <p>Tổng: ${Number(order.totalAmount || 0).toFixed(2)}</p>
                <p>Đơn giá: ${Number(order.unitPrice || 0).toFixed(2)}</p>
                <p>{formatDate(order.createdAt)}</p>
              </div>

              <div className="mt-4 flex items-center justify-end">
                <button
                  type="button"
                  disabled={!canCancel(order) || loading}
                  onClick={() => onCancel(order.id)}
                  className={`inline-flex items-center gap-1 rounded-full px-4 py-2 text-xs font-semibold transition ${
                    canCancel(order) && !loading
                      ? 'bg-slate-950 text-white hover:bg-slate-800'
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
