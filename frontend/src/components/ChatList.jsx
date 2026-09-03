import { useState } from 'react';
import api from '../services/api';
import { useChatStore } from '../store/chatStore';
import { useContactStore } from '../store/contactStore';
import PresenceDot from './PresenceDot';

export default function ChatList() {
  const { chats, activeChatId, presence, selectChat, openChatWith, leaveChat } = useChatStore();
  const { sendRequest, isContact, hasRequested } = useContactStore();
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

  const request = async (userId) => {
    // 409 means the request already exists, which is not worth surfacing.
    await sendRequest(userId).catch(() => {});
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
              {/*
                A chat can only be opened with an accepted contact — the server
                refuses otherwise. So the name is a button only once you are
                connected; before that the row offers a request instead.
              */}
              {isContact(user.id) ? (
                <button type="button" onClick={() => startChat(user.id)}>
                  {user.displayName}
                  <span className="muted"> @{user.username}</span>
                </button>
              ) : (
                <span className="search-name">
                  {user.displayName}
                  <span className="muted"> @{user.username}</span>
                </span>
              )}

              {!isContact(user.id) &&
                (hasRequested(user.id) ? (
                  <span className="muted requested">Requested</span>
                ) : (
                  <button type="button" className="link add-contact" onClick={() => request(user.id)}>
                    Add friend
                  </button>
                ))}
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
              <div className="chat-name">
                {/* Group chats have many members, so a single dot would be a lie. */}
                {!chat.group && chat.otherUserId && <PresenceDot presence={presence[chat.otherUserId]} />}
                {chat.title ?? chat.otherUserName ?? 'Conversation'}
              </div>
              <div className="chat-preview">{chat.lastMessage ?? 'No messages yet'}</div>
              {chat.unreadCount > 0 && <span className="badge">{chat.unreadCount}</span>}
            </button>
            <button
              type="button"
              className="link leave-chat"
              title="Leave this conversation"
              onClick={() => leaveChat(chat.id)}
            >
              Leave
            </button>
          </li>
        ))}
      </ul>
    </aside>
  );
}
