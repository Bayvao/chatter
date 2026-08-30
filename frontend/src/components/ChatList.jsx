import { useState } from 'react';
import api from '../services/api';
import { useChatStore } from '../store/chatStore';

export default function ChatList() {
  const { chats, activeChatId, selectChat, openChatWith } = useChatStore();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);

  const onSearch = async (event) => {
    event.preventDefault();
    const term = query.trim();
    if (!term) {
      setResults([]);
      return;
    }

    setSearching(true);
    const { data } = await api.get('/api/users/search', { params: { q: term } });
    setResults(data);
    setSearching(false);
  };

  const startChat = async (userId) => {
    await openChatWith(userId);
    setQuery('');
    setResults([]);
  };

  const addContact = async (userId) => {
    // 409 just means they are already a contact, which is not worth surfacing.
    await api.post(`/api/users/me/contacts/${userId}`).catch(() => {});
    setResults(results.filter((user) => user.id !== userId));
  };

  return (
    <aside className="chat-list">
      <form className="search" onSubmit={onSearch}>
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Find someone to chat with"
          aria-label="Search users"
        />
        <button type="submit" disabled={searching}>
          Search
        </button>
      </form>

      {results.length > 0 && (
        <ul className="search-results">
          {results.map((user) => (
            <li key={user.id}>
              <button type="button" onClick={() => startChat(user.id)}>
                {user.displayName}
                <span className="muted"> @{user.username}</span>
              </button>
              <button type="button" className="link add-contact" onClick={() => addContact(user.id)}>
                Add contact
              </button>
            </li>
          ))}
        </ul>
      )}

      <ul className="chats">
        {chats.length === 0 && <li className="empty">No conversations yet</li>}

        {chats.map((chat) => (
          <li key={chat.id}>
            <button
              type="button"
              className={chat.id === activeChatId ? 'active' : ''}
              onClick={() => selectChat(chat.id)}
            >
              <div className="chat-name">{chat.title ?? chat.otherUserName ?? 'Conversation'}</div>
              <div className="chat-preview">{chat.lastMessage ?? 'No messages yet'}</div>
              {chat.unreadCount > 0 && <span className="badge">{chat.unreadCount}</span>}
            </button>
          </li>
        ))}
      </ul>
    </aside>
  );
}
