import { useEffect, useRef } from 'react';
import { useAuthStore } from '../store/authStore';
import { useChatStore } from '../store/chatStore';
import websocket from '../services/websocket';
import ChatList from './ChatList';
import MessageBubble from './MessageBubble';
import MessageInput from './MessageInput';

export default function ChatWindow() {
  const { user, token, logout } = useAuthStore();
  const { chats, activeChatId, messages, loadingMessages, loadChats, receiveMessage, reset } = useChatStore();
  const bottomRef = useRef(null);

  useEffect(() => {
    loadChats();
    websocket.connect(token, { onConnect: () => loadChats() });

    return () => websocket.disconnect();
  }, [token, loadChats]);

  // Re-subscribe whenever the open conversation changes; the topic is
  // per-chat and the server checks membership on SUBSCRIBE.
  useEffect(() => {
    if (!activeChatId) {
      return undefined;
    }

    const subscribe = () => websocket.subscribeToChat(activeChatId, receiveMessage);
    subscribe();
    // The socket may still be connecting on first render.
    const retry = setTimeout(subscribe, 500);

    return () => {
      clearTimeout(retry);
      websocket.unsubscribeFromChat(activeChatId);
    };
  }, [activeChatId, receiveMessage]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const activeChat = chats.find((chat) => chat.id === activeChatId);

  const onLogout = () => {
    websocket.disconnect();
    reset();
    logout();
  };

  return (
    <div className="app">
      <header className="app-header">
        <span className="brand">Chatter</span>
        <span className="spacer" />
        <span className="muted">{user?.displayName ?? user?.username}</span>
        <button type="button" className="link" onClick={onLogout}>
          Sign out
        </button>
      </header>

      <div className="app-body">
        <ChatList />

        <main className="conversation">
          {!activeChatId && <div className="placeholder">Pick a conversation, or search for someone.</div>}

          {activeChatId && (
            <>
              <div className="conversation-header">
                {activeChat?.title ?? activeChat?.otherUserName ?? 'Conversation'}
              </div>

              <div className="messages">
                {loadingMessages && <div className="placeholder">Loading…</div>}
                {!loadingMessages && messages.length === 0 && (
                  <div className="placeholder">No messages yet. Say hello.</div>
                )}
                {messages.map((message) => (
                  <MessageBubble key={message.id} message={message} own={message.senderId === user?.id} />
                ))}
                <div ref={bottomRef} />
              </div>

              <MessageInput chatId={activeChatId} disabled={loadingMessages} />
            </>
          )}
        </main>
      </div>
    </div>
  );
}
