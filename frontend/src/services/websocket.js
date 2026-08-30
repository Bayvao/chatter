import { Client } from '@stomp/stompjs';

const WS_URL = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080/ws';

/**
 * One STOMP connection per signed-in session. The token goes on the CONNECT
 * frame, which is where the server authenticates it — the handshake itself
 * carries no credentials.
 */
class WebSocketService {
  constructor() {
    this.client = null;
    this.subscriptions = new Map();
  }

  connect(token, { onConnect, onError } = {}) {
    this.disconnect();

    this.client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => onConnect?.(),
      onStompError: (frame) => onError?.(frame.headers?.message ?? 'STOMP error'),
      onWebSocketError: () => onError?.('WebSocket connection failed'),
    });

    this.client.activate();
  }

  subscribeToChat(chatId, callback) {
    if (!this.client?.connected) {
      return;
    }
    this.unsubscribeFromChat(chatId);

    const subscription = this.client.subscribe(`/topic/chats/${chatId}`, (frame) => {
      callback(JSON.parse(frame.body));
    });
    this.subscriptions.set(chatId, subscription);
  }

  unsubscribeFromChat(chatId) {
    this.subscriptions.get(chatId)?.unsubscribe();
    this.subscriptions.delete(chatId);
  }

  sendMessage(chatId, content, clientMsgId) {
    if (!this.client?.connected) {
      throw new Error('Not connected');
    }

    this.client.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ chatId, content, clientMsgId }),
    });
  }

  get connected() {
    return Boolean(this.client?.connected);
  }

  disconnect() {
    this.subscriptions.forEach((subscription) => subscription.unsubscribe());
    this.subscriptions.clear();
    this.client?.deactivate();
    this.client = null;
  }
}

export default new WebSocketService();
