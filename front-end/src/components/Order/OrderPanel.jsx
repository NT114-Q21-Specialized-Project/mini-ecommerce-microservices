import React, { useMemo } from 'react';
import { Circle, CreditCard, RotateCw } from 'lucide-react';

const OrderPanel = ({ orders, onRefresh, products }) => {
  const latestOrder = orders[0] || null;
  const totalAmount = orders.reduce((sum, order) => sum + Number(order.totalAmount || 0), 0);
  const activeCount = orders.filter((order) => !['FAILED', 'CANCELLED'].includes(order.status)).length;

  const selectedProduct = useMemo(() => {
    if (!products.length) {
      return null;
    }

    if (!latestOrder) {
      return products[0];
    }

    return products.find((product) => product.id === latestOrder.productId) || products[0];
  }, [latestOrder, products]);

  return (
    <aside className="fashion-panel rounded-[34px] border p-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.3em] text-sky-600">Order Information</p>
          <h2 className="mt-3 text-[22px] font-bold text-slate-900">Order Pulse</h2>
        </div>
        <button
          type="button"
          onClick={onRefresh}
          className="rounded-full border border-sky-100 bg-white px-4 py-2 text-sm font-semibold text-sky-700 transition hover:bg-sky-50"
        >
          <span className="inline-flex items-center gap-1">
            <RotateCw className="h-4 w-4" />
            Refresh
          </span>
        </button>
      </div>

      <div className="mt-6 rounded-[28px] bg-gradient-to-br from-cyan-400 via-sky-500 to-blue-700 p-5 text-white shadow-[0_20px_60px_rgba(37,99,235,0.25)]">
        <div className="flex items-center justify-between">
          <p className="text-[11px] font-bold uppercase tracking-[0.3em] text-white/70">Jajan Card</p>
          <p className="text-lg font-bold">VISA</p>
        </div>
        <p className="mt-8 text-base font-medium tracking-[0.35em]">1234 **** **** ****</p>
        <div className="mt-8 flex items-end justify-between">
          <div>
            <p className="text-[10px] uppercase tracking-[0.24em] text-white/60">Card Holder</p>
            <p className="mt-1 text-sm font-semibold">{activeCount > 0 ? 'MINI STORE' : 'NO ACTIVE ORDER'}</p>
          </div>
          <div className="text-right">
            <p className="text-[10px] uppercase tracking-[0.24em] text-white/60">Expired</p>
            <p className="mt-1 text-sm font-semibold">10/26</p>
          </div>
        </div>
      </div>

      <section className="mt-6">
        <h3 className="text-sm font-semibold text-slate-900">Payment</h3>
        <div className="mt-4 space-y-3">
          {[
            ['Credit Card', true],
            ['Paypal', false],
          ].map(([label, active]) => (
            <div key={label} className="flex items-center justify-between rounded-[18px] bg-slate-50 px-4 py-3">
              <div className="inline-flex items-center gap-3">
                <span
                  className={`flex h-4 w-4 items-center justify-center rounded-full border ${
                    active ? 'border-sky-400' : 'border-slate-300'
                  }`}
                >
                  {active ? <span className="h-2 w-2 rounded-full bg-sky-500" /> : null}
                </span>
                <span className="text-sm font-medium text-slate-700">{label}</span>
              </div>
              <Circle className={`h-3 w-3 ${active ? 'fill-orange-500 text-orange-500' : 'text-slate-300'}`} />
            </div>
          ))}
        </div>
      </section>

      <section className="mt-6 rounded-[22px] bg-slate-50 p-4">
        <div className="flex items-start gap-3">
          <div className="flex h-14 w-14 items-center justify-center rounded-[16px] bg-gradient-to-br from-cyan-200 to-sky-300 text-sky-700">
            <CreditCard className="h-6 w-6" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-slate-900">{selectedProduct?.name || 'No selected product'}</p>
            <p className="mt-1 text-xs text-slate-500">
              {selectedProduct ? `Colour: Blue Dark` : 'Hãy tạo hoặc chọn order để thấy chi tiết.'}
            </p>
          </div>
        </div>

        <div className="mt-4 space-y-2 text-sm text-slate-500">
          <div className="flex items-center justify-between">
            <span>Subtotal</span>
            <span className="font-medium text-slate-400">
              {latestOrder ? `$${Number(latestOrder.totalAmount || 0).toFixed(2)}` : '$0.00'}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span>Shipping</span>
            <span className="font-medium text-sky-500">Free</span>
          </div>
        </div>

        <div className="mt-5 rounded-full bg-gradient-to-r from-sky-500 to-blue-600 px-5 py-3 text-center text-sm font-semibold text-white">
          Total ${totalAmount.toFixed(2)}
        </div>
      </section>
    </aside>
  );
};

export default OrderPanel;
