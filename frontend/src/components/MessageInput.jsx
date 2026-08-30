import { useState } from 'react';
import websocket from '../services/websocket';
import api from '../services/api';

export default function MessageInput({ chatId, disabled }) {
  const [content, setContent] = useState('');

  const onSubmit = async (event) => {
    event.preventDefault();

    const trimmed = content.trim();
    if (!trimmed) {
      return;
    }

    // A stable id makes a retry after a dropped connection idempotent
    // server-side rather than posting the message twice.
    const clientMsgId = crypto.randomUUID();
    setContent('');

    if (websocket.connected) {
      websocket.sendMessage(chatId, trimmed, clientMsgId);
    } else {
      await api.post('/api/chats/messages', { chatId, content: trimmed, clientMsgId });
    }
  };

  return (
    <form className="message-input" onSubmit={onSubmit}>
      <input
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="Type a message"
        disabled={disabled}
        aria-label="Message"
      />
      <button type="submit" disabled={disabled || !content.trim()}>
        Send
      </button>
    </form>
  );
}
