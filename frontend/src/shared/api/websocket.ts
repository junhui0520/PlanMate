/*
 * WebSocket 연결 기본 설정 (AGENTS §7: 실시간 연결은 이 파일을 거친다).
 * 브라우저 기본 WebSocket만 쓴다(라이브러리 없음). 로그인 쿠키는 브라우저가 연결할 때 같이 보낸다.
 * TODO(B에 요청): 연결 주소·메시지 형식·재연결 정책 (COL-06-01)
 */

export interface SocketHandlers<TMessage> {
  onMessage?: (message: TMessage) => void;
  onOpen?: () => void;
  onClose?: (event: CloseEvent) => void;
  onError?: (event: Event) => void;
}

export interface SocketConnection {
  /** JSON으로 바꿔서 보낸다. 열리기 전(isOpen() false)에 보내면 브라우저가 오류를 낸다 */
  send(data: unknown): void;
  close(): void;
  isOpen(): boolean;
}

const baseUrl: string | undefined = import.meta.env.VITE_API_BASE_URL;

// http -> ws, https -> wss
function toSocketUrl(path: string): string {
  if (!baseUrl) {
    throw new Error('서버 주소가 없어요. frontend/.env.local에 VITE_API_BASE_URL 확인');
  }
  return `${baseUrl.replace(/\/+$/, '').replace(/^http/, 'ws')}${path}`;
}

/** 서버와 실시간 연결을 연다. 받은 메시지는 JSON으로 풀어서 onMessage에 넘긴다 */
export function connectSocket<TMessage>(path: string, handlers: SocketHandlers<TMessage>): SocketConnection {
  const socket = new WebSocket(toSocketUrl(path));

  socket.addEventListener('open', () => handlers.onOpen?.());
  socket.addEventListener('close', (event) => handlers.onClose?.(event));
  socket.addEventListener('error', (event) => handlers.onError?.(event));
  socket.addEventListener('message', (event: MessageEvent<unknown>) => {
    if (typeof event.data !== 'string') {
      if (import.meta.env.DEV) console.warn('[websocket] 문자열이 아닌 메시지를 건너뜀');
      return;
    }
    let message: TMessage;
    try {
      message = JSON.parse(event.data);
    } catch {
      // JSON이 아닌 메시지는 건너뛴다
      if (import.meta.env.DEV) console.warn('[websocket] JSON이 아닌 메시지를 건너뜀');
      return;
    }
    handlers.onMessage?.(message);
  });

  return {
    send: (data) => socket.send(JSON.stringify(data)),
    close: () => socket.close(),
    isOpen: () => socket.readyState === WebSocket.OPEN,
  };
}
