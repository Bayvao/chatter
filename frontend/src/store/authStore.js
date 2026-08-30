import { create } from 'zustand';
import api, { setAuthToken } from '../services/api';

const TOKEN_KEY = 'chatter.token';

const storedToken = localStorage.getItem(TOKEN_KEY);
if (storedToken) {
  setAuthToken(storedToken);
}

export const useAuthStore = create((set) => ({
  token: storedToken,
  user: null,
  // True until the stored token has been checked, so the router does not
  // bounce a signed-in user to /login on a page refresh.
  loading: Boolean(storedToken),
  error: null,

  async restoreSession() {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      set({ loading: false });
      return;
    }

    setAuthToken(token);
    try {
      const { data } = await api.get('/api/auth/me');
      set({ token, user: data, loading: false });
    } catch {
      localStorage.removeItem(TOKEN_KEY);
      setAuthToken(null);
      set({ token: null, user: null, loading: false });
    }
  },

  async login(username, password) {
    set({ error: null });
    try {
      const { data } = await api.post('/api/auth/login', { username, password });
      localStorage.setItem(TOKEN_KEY, data.token);
      setAuthToken(data.token);
      set({ token: data.token, user: data.user });
      return true;
    } catch (err) {
      set({ error: err.response?.data?.message ?? 'Login failed' });
      return false;
    }
  },

  async register(username, email, password) {
    set({ error: null });
    try {
      const { data } = await api.post('/api/auth/register', { username, email, password });
      localStorage.setItem(TOKEN_KEY, data.token);
      setAuthToken(data.token);
      set({ token: data.token, user: data.user });
      return true;
    } catch (err) {
      set({ error: err.response?.data?.message ?? 'Registration failed' });
      return false;
    }
  },

  logout() {
    localStorage.removeItem(TOKEN_KEY);
    setAuthToken(null);
    set({ token: null, user: null, error: null });
  },
}));
