import { create } from 'zustand';
import api from '../services/api';

/**
 * Contacts and the requests either side of them.
 *
 * <p>REST is the source of truth on load; the live events from
 * /user/queue/contacts only save a poll. A request that arrives while the tab
 * is closed is picked up by the next load, so nothing depends on the socket.
 */
export const useContactStore = create((set, get) => ({
  contacts: [],
  incoming: [],
  outgoing: [],

  async loadAll() {
    const [contacts, incoming, outgoing] = await Promise.all([
      api.get('/api/users/me/contacts'),
      api.get('/api/users/me/contacts/requests'),
      api.get('/api/users/me/contacts/requests/sent'),
    ]);
    set({ contacts: contacts.data, incoming: incoming.data, outgoing: outgoing.data });
  },

  /**
   * Sends a friend request. The chat does not open — the two of them are not
   * contacts until this is accepted, which is the whole point.
   */
  async sendRequest(userId) {
    await api.post(`/api/users/me/contacts/${userId}`);
    await get().loadAll();
  },

  async accept(userId) {
    await api.post(`/api/users/me/contacts/${userId}/accept`);
    await get().loadAll();
  },

  async decline(userId) {
    await api.delete(`/api/users/me/contacts/${userId}/decline`);
    await get().loadAll();
  },

  async remove(userId) {
    await api.delete(`/api/users/me/contacts/${userId}`);
    await get().loadAll();
  },

  async block(userId) {
    await api.post(`/api/users/me/contacts/${userId}/block`);
    await get().loadAll();
  },

  /**
   * Applied to every frame on /user/queue/contacts.
   *
   * <p>Refetches rather than patching the arrays in place: these lists are
   * small, the events are rare, and a refetch cannot drift from the server the
   * way incremental edits can.
   */
  receiveEvent() {
    get().loadAll();
  },

  /** Whether a chat may be opened with this user — the gate the API enforces. */
  isContact(userId) {
    return get().contacts.some(({ user }) => user.id === userId);
  },

  /** Whether a request to this user is already outstanding. */
  hasRequested(userId) {
    return get().outgoing.some(({ user }) => user.id === userId);
  },

  reset() {
    set({ contacts: [], incoming: [], outgoing: [] });
  },
}));
