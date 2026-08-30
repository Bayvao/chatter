import { useEffect, useState } from 'react';
import api from '../services/api';
import { useAuthStore } from '../store/authStore';

const FIELDS = [
  ['firstName', 'First name'],
  ['lastName', 'Last name'],
  ['statusText', 'Status'],
  ['phoneNumber', 'Phone'],
  ['bio', 'Bio'],
  ['location', 'Location'],
  ['website', 'Website'],
  ['avatarUrl', 'Avatar URL'],
];

export default function Profile({ onClose }) {
  const { setUser } = useAuthStore();
  const [form, setForm] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    api.get('/api/users/me/profile').then(({ data }) => setForm(data));
  }, []);

  const onSubmit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setSaved(false);

    const payload = Object.fromEntries(FIELDS.map(([key]) => [key, form[key] ?? '']));
    const { data } = await api.put('/api/users/me/profile', payload);

    setForm(data);
    // Keep the header in sync with a changed display name.
    setUser((current) => (current ? { ...current, displayName: data.displayName } : current));
    setSaving(false);
    setSaved(true);
  };

  if (!form) {
    return <div className="placeholder">Loading profile…</div>;
  }

  return (
    <form className="profile" onSubmit={onSubmit}>
      <div className="conversation-header">
        Your profile
        <button type="button" className="link" onClick={onClose}>
          Close
        </button>
      </div>

      <div className="profile-fields">
        {FIELDS.map(([key, label]) => (
          <label key={key}>
            <span>{label}</span>
            <input
              value={form[key] ?? ''}
              onChange={(e) => setForm({ ...form, [key]: e.target.value })}
            />
          </label>
        ))}

        <button type="submit" disabled={saving}>
          {saving ? 'Saving…' : 'Save'}
        </button>
        {saved && <span className="muted">Saved</span>}
      </div>
    </form>
  );
}
