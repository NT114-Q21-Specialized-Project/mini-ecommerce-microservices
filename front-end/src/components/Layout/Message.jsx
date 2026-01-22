import React from 'react';
import { CheckCircle, AlertCircle } from 'lucide-react';

const Message = ({ message }) => {
  if (!message.text) return null;

  return (
    <div className={`fixed top-5 right-5 z-50 p-4 rounded-xl shadow-2xl flex items-center animate-bounce text-white ${
      message.type === 'success' ? 'bg-green-500' : 'bg-red-500'
    }`}>
      {message.type === 'success' ? <CheckCircle className="mr-2" /> : <AlertCircle className="mr-2" />}
      {message.text}
    </div>
  );
};

export default Message;