import { create } from 'zustand';
import api from '../services/api';

export const useChatStore = create((set, get) => ({
  chats: [],
  activeChatId: null,
  messages: [],
  loadingMessages: false,
  /** userId -> { online, lastSeen } */
  presence: {},
  /** chatId -> highest seq this client holds; what a reconnect syncs from. */
  cursors: {},

  async loadChats() {
    const { data } = await api.get('/api/chats');
    set({ chats: data });
    await get().loadPresence();
  },

  /**
   * One request for every conversation partner, for the initial render.
   * After this, changes arrive on /topic/presence rather than by polling.
   */
  async loadPresence() {
    const userIds = get()
      .chats.map((chat) => chat.otherUserId)
      .filter(Boolean);

    if (userIds.length === 0) {
      return;
    }

    const { data } = await api.get('/api/presence', { params: { userIds: userIds.join(',') } });
    set((state) => ({
      presence: data.reduce(
        (acc, entry) => ({ ...acc, [entry.userId]: { online: entry.online, lastSeen: entry.lastSeen } }),
        state.presence,
      ),
    }));
  },

  setPresence({ userId, online, lastSeen }) {
    set((state) => ({ presence: { ...state.presence, [userId]: { online, lastSeen } } }));
  },

  async openChatWith(userId) {
    const { data } = await api.post(`/api/chats/with/${userId}`);
    await get().loadChats();
    await get().selectChat(data.id);
    return data;
  },

  async selectChat(chatId) {
    set({ activeChatId: chatId, loadingMessages: true, messages: [] });

    const { data } = await api.get(`/api/chats/${chatId}/messages`);
    // The API returns newest-first for paging; the view reads oldest-first.
    const messages = [...data].reverse();
    set((state) => ({
      messages,
      loadingMessages: false,
      cursors: { ...state.cursors, [chatId]: highestSeq(messages, state.cursors[chatId]) },
    }));
  },

  /**
   * Applied to every message arriving over the socket, including the ones
   * this client sent — the server echoes them back on the same topic, so
   * there is no optimistic copy to reconcile.
   */
  receiveMessage(message) {
    const { activeChatId, messages } = get();

    if (message.chatId === activeChatId && !messages.some((m) => m.id === message.id)) {
      // Sync batches can arrive out of order relative to live traffic, so
      // sort by seq rather than trusting arrival order.
      set({ messages: [...messages, message].sort((a, b) => a.seq - b.seq) });
    }

    set((state) => ({
      cursors: {
        ...state.cursors,
        [message.chatId]: Math.max(state.cursors[message.chatId] ?? 0, message.seq ?? 0),
      },
      chats: state.chats.map((chat) =>
        chat.id === message.chatId
          ? {
              ...chat,
              lastMessage: message.content,
              lastMessageAt: message.createdAt,
              unreadCount: chat.id === state.activeChatId ? 0 : chat.unreadCount + 1,
            }
          : chat,
      ),
    }));
  },

  /**
   * A batch of messages missed while disconnected. Routed through
   * receiveMessage so the de-dupe by id and the unread counts behave exactly
   * as they do for live traffic.
   */
  receiveSyncBatch({ messages = [] } = {}) {
    messages.forEach((message) => get().receiveMessage(message));
  },

  reset() {
    set({
      chats: [],
      activeChatId: null,
      messages: [],
      loadingMessages: false,
      presence: {},
      cursors: {},
    });
  },
}));

function highestSeq(messages, fallback = 0) {
  return messages.reduce((max, message) => Math.max(max, message.seq ?? 0), fallback ?? 0);
}
