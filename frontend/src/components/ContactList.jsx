import { useEffect, useState } from 'react';
import api from '../services/api';
import { useChatStore } from '../store/chatStore';

export default function ContactList() {
  const { openChatWith } = useChatStore();
  const [contacts, setContacts] = useState([]);

  const load = () => api.get('/api/users/me/contacts').then(({ data }) => setContacts(data));

  useEffect(() => {
    load();
  }, []);

  const remove = async (userId) => {
    await api.delete(`/api/users/me/contacts/${userId}`);
    load();
  };

  const block = async (userId) => {
    await api.post(`/api/users/me/contacts/${userId}/block`);
    load();
  };

  if (contacts.length === 0) {
    return <div className="empty">No contacts yet. Search for someone and add them.</div>;
  }

  return (
    <ul className="contacts">
      {contacts.map(({ user }) => (
        <li key={user.id}>
          <button type="button" className="contact-name" onClick={() => openChatWith(user.id)}>
            {user.displayName}
            <span className="muted"> @{user.username}</span>
          </button>
          <span className="contact-actions">
            <button type="button" className="link" onClick={() => block(user.id)}>
              Block
            </button>
            <button type="button" className="link" onClick={() => remove(user.id)}>
              Remove
            </button>
          </span>
        </li>
      ))}
    </ul>
  );
}
