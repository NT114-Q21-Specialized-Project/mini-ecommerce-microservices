import React from 'react';
import { AlertTriangle, CheckCircle2 } from 'lucide-react';

const Message = ({ message }) => {
  if (!message.text) {
    return null;
  }

  const isSuccess = message.type === 'success';

  return (
    <div
      className={`pointer-events-none fixed right-4 top-4 z-50 animate-slide-up rounded-2xl border px-4 py-3 text-sm font-medium shadow-xl md:right-8 ${
        isSuccess
          ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
          : 'border-rose-200 bg-rose-50 text-rose-800'
      }`}
    >
      <div className="flex items-center gap-2">
        {isSuccess ? <CheckCircle2 className="h-4 w-4" /> : <AlertTriangle className="h-4 w-4" />}
        <span>{message.text}</span>
      </div>
    </div>
  );
};

export default Message;
