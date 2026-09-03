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
    this.hasConnectedBefore = false;
  }

  connect(token, { onConnect, onError, onReconnect } = {}) {
    this.disconnect();

    this.client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        const reconnected = this.hasConnectedBefore;
        this.hasConnectedBefore = true;
        onConnect?.();
        // stompjs reconnects on its own, and a dropped socket is exactly when
        // messages go missing. Catching up is the reconnect handler's job.
        if (reconnected) {
          onReconnect?.();
        }
      },
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

  /**
   * Relationship changes addressed to this user: requests, accepts, declines
   * and removals.
   *
   * <p>A user destination, so Spring scopes it to this session and nobody else
   * can subscribe to it. Best effort — an event fired while this client was
   * disconnected is simply missed, and the REST lists cover that on next load.
   */
  subscribeToContacts(callback) {
    return this.subscribe('contacts', '/user/queue/contacts', callback);
  }

  /**
   * Failures the server could not answer inline — a send refused because a
   * block stands between the two parties, most of all.
   *
   * <p>Without this the refusal arrives as a STOMP ERROR frame and the session
   * closes, so the client sees a dropped connection rather than a reason.
   */
  subscribeToErrors(callback) {
    return this.subscribe('errors', '/user/queue/errors', callback);
  }

  /** Connect/disconnect of every user, broadcast server-side on session events. */
  subscribeToPresence(callback) {
    return this.subscribe('presence', '/topic/presence', callback);
  }

  /**
   * The two queues `/app/sync.start` answers on: batches of up to 50 missed
   * messages, then a single completion frame with the total.
   */
  subscribeToSync({ onBatch, onComplete } = {}) {
    this.subscribe('sync-batch', '/user/queue/sync-batch', (payload) => onBatch?.(payload));
    this.subscribe('sync-complete', '/user/queue/sync-complete', (payload) => onComplete?.(payload));
  }

  /**
   * Asks for everything past the given per-chat `seq` cursors. Sending a
   * timestamp instead would be ambiguous — clocks drift, and two messages can
   * share a millisecond — so the server takes cursors only.
   */
  startSync(cursors) {
    if (!this.client?.connected || Object.keys(cursors ?? {}).length === 0) {
      return;
    }

    this.client.publish({
      destination: '/app/sync.start',
      body: JSON.stringify({ cursors }),
    });
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
    this.hasConnectedBefore = false;
  }

  /** Keyed so a re-render replaces a subscription rather than doubling it. */
  subscribe(key, destination, callback) {
    if (!this.client?.connected) {
      return undefined;
    }
    this.subscriptions.get(key)?.unsubscribe();

    const subscription = this.client.subscribe(destination, (frame) => {
      callback(JSON.parse(frame.body));
    });
    this.subscriptions.set(key, subscription);
    return subscription;
  }
}

export default new WebSocketService();
