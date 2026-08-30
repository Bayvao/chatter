import { useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import ChatWindow from './components/ChatWindow';
import Login from './components/Login';
import Register from './components/Register';

export default function App() {
  const { token, loading, restoreSession } = useAuthStore();

  useEffect(() => {
    restoreSession();
  }, [restoreSession]);

  if (loading) {
    return <div className="placeholder full">Loading…</div>;
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/chat" element={token ? <ChatWindow /> : <Navigate to="/login" replace />} />
        <Route path="*" element={<Navigate to={token ? '/chat' : '/login'} replace />} />
      </Routes>
    </BrowserRouter>
  );
}
