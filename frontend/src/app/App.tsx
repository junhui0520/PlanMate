import { ToastProvider } from '../shared/components/Toast';
import { AppRouter } from './router';

/** 최상위 화면: 상태 창 Provider + 라우터 */
export function App() {
  return (
    <ToastProvider>
      <AppRouter />
    </ToastProvider>
  );
}
