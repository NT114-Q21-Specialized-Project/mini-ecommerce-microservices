import React, { useEffect, useState } from 'react';
import { ImagePlus } from 'lucide-react';

const MediaSlot = ({ src, alt, title, hint, className = '', imgClassName = '' }) => {
  const [imageFailed, setImageFailed] = useState(false);

  useEffect(() => {
    setImageFailed(false);
  }, [src]);

  return (
    <div className={`media-slot-shell ${className}`}>
      {src && !imageFailed ? (
        <img
          src={src}
          alt={alt}
          className={imgClassName || 'h-full w-full object-cover'}
          onError={() => setImageFailed(true)}
        />
      ) : (
        <div className="media-placeholder h-full w-full">
          <ImagePlus className="h-6 w-6 text-cyan-600" />
          <p className="mt-3 text-sm font-semibold text-slate-900">{title}</p>
          {hint ? <p className="mt-1 text-xs text-slate-500">{hint}</p> : null}
        </div>
      )}
    </div>
  );
};

export default MediaSlot;
