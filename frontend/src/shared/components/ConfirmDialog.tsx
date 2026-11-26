import type { ReactNode } from 'react';
import { Button } from './Button';
import { Modal } from './Modal';

export interface ConfirmDialogProps {
  open: boolean;
  message: ReactNode;
  confirmLabel?: string; // 기본 '확인'. [삭제]·[해제]처럼 부르는 쪽이 바꾼다
  cancelLabel?: string; // 기본 '취소'
  hideCancel?: boolean; // [확인]만 있는 창
  onConfirm: () => void;
  onCancel?: () => void;
  confirmLoading?: boolean;
  confirmLoadingText?: string;
}

/**
 * 확인·취소 창 (Figma 확인 창: 오른쪽 아래 [취소](보조) [확인](주요)).
 * 처리 중(confirmLoading)에는 [확인]이 비활성되고 [취소]도 함께 막는다.
 */
export function ConfirmDialog({
  open,
  message,
  confirmLabel = '확인',
  cancelLabel = '취소',
  hideCancel = false,
  onConfirm,
  onCancel,
  confirmLoading = false,
  confirmLoadingText,
}: ConfirmDialogProps) {
  return (
    <Modal
      open={open}
      size="sm"
      footer={
        <>
          {!hideCancel && (
            <Button variant="secondary" onClick={() => onCancel?.()} disabled={confirmLoading}>
              {cancelLabel}
            </Button>
          )}
          <Button
            variant="primary"
            onClick={() => onConfirm()}
            loading={confirmLoading}
            loadingText={confirmLoadingText}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      {message}
    </Modal>
  );
}
