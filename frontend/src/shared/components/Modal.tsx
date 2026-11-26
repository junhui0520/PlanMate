import { useEffect, useId, useRef } from 'react';
import type { ReactNode } from 'react';
import { createPortal } from 'react-dom';

export interface ModalProps {
  open: boolean;
  title?: string;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
}

/**
 * 공통 팝업 창. document.body에 그리고 배경을 어둡게 한다.
 * 열려 있는 동안 body 스크롤 잠금, 열릴 때 창 안으로 포커스 이동.
 * ESC·배경 클릭으로 닫지 않는다(문서에 없음). 닫기는 footer 버튼으로만.
 */
export function Modal({ open, title, children, footer, size = 'md' }: ModalProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialogRef.current?.focus();
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  if (!open) return null;

  return createPortal(
    <div className="pm-modal-backdrop">
      <div
        ref={dialogRef}
        className={`pm-modal pm-modal--${size}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        tabIndex={-1}
      >
        {title && (
          <h2 id={titleId} className="pm-modal__title">
            {title}
          </h2>
        )}
        <div className="pm-modal__body">{children}</div>
        {footer && <div className="pm-modal__footer">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}
