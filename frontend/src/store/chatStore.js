import { create } from 'zustand';
import api from '../services/api';

export const useChatStore = create((set, get) => ({
  chats: [],
  activeChatId: null,
  messages: [],
  loadingMessages: false,

  async loadChats() {
    const { data } = await api.get('/api/chats');
    set({ chats: data });
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
    set({ messages: [...data].reverse(), loadingMessages: false });
  },

  /**
   * Applied to every message arriving over the socket, including the ones
   * this client sent — the server echoes them back on the same topic, so
   * there is no optimistic copy to reconcile.
   */
  receiveMessage(message) {
    const { activeChatId, messages } = get();

    if (message.chatId === activeChatId && !messages.some((m) => m.id === message.id)) {
      set({ messages: [...messages, message] });
    }

    set((state) => ({
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

  reset() {
    set({ chats: [], activeChatId: null, messages: [], loadingMessages: false });
  },
}));
