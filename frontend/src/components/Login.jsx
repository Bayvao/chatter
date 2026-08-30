import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';

export default function Login() {
  const { login, error, token } = useAuthStore();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();

  if (token) {
    return <Navigate to="/chat" replace />;
  }

  const onSubmit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    const ok = await login(username, password);
    setSubmitting(false);
    if (ok) {
      navigate('/chat');
    }
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>Chatter</h1>
        <p className="auth-subtitle">Sign in to continue</p>

        {error && <div className="error">{error}</div>}

        <label htmlFor="username">Username</label>
        <input
          id="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          required
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />

        <button type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>

        <p className="auth-switch">
          No account? <Link to="/register">Create one</Link>
        </p>
      </form>
    </div>
  );
}
