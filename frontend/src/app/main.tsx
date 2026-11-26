import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '../shared/styles/global.css'; // variables.css는 global.css가 불러온다
import { App } from './App';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('index.html에 #root가 없어요.');
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
