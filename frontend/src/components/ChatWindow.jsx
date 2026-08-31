import { useEffect, useRef, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import { useChatStore } from '../store/chatStore';
import websocket from '../services/websocket';
import { disablePush, enablePush } from '../services/push';
import ChatList from './ChatList';
import ContactList from './ContactList';
import MessageBubble from './MessageBubble';
import MessageInput from './MessageInput';
import PresenceDot from './PresenceDot';
import Profile from './Profile';

export default function ChatWindow() {
  const { user, token, logout } = useAuthStore();
  const {
    chats,
    activeChatId,
    messages,
    loadingMessages,
    presence,
    loadChats,
    receiveMessage,
    receiveSyncBatch,
    setPresence,
    selectChat,
    reset,
  } = useChatStore();
  const bottomRef = useRef(null);
  const [sidebarTab, setSidebarTab] = useState('chats');
  const [showProfile, setShowProfile] = useState(false);

  useEffect(() => {
    loadChats();

    websocket.connect(token, {
      onConnect: () => {
        loadChats();
        websocket.subscribeToPresence(setPresence);
        websocket.subscribeToSync({ onBatch: receiveSyncBatch });
      },
      // The socket drops silently; this is where anything sent in the gap is
      // recovered. Cursors are read at fire time, not closed over.
      onReconnect: () => websocket.startSync(useChatStore.getState().cursors),
    });

    // Ask once per session, after sign-in rather than on page load, so the
    // permission prompt has visible context.
    enablePush();

    return () => websocket.disconnect();
  }, [token, loadChats, receiveSyncBatch, setPresence]);

  // The service worker focuses this tab on a notification click and tells us
  // which conversation it was for.
  useEffect(() => {
    if (!('serviceWorker' in navigator)) {
      return undefined;
    }

    const onMessage = (event) => {
      if (event.data?.type === 'notification-click' && event.data.chatId) {
        selectChat(event.data.chatId);
      }
    };

    navigator.serviceWorker.addEventListener('message', onMessage);
    return () => navigator.serviceWorker.removeEventListener('message', onMessage);
  }, [selectChat]);

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

  const onLogout = async () => {
    // Retire the push subscription first: after logout the API call that
    // identifies it would no longer be authenticated.
    await disablePush();
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
        <button type="button" className="link" onClick={() => setShowProfile((open) => !open)}>
          {showProfile ? 'Back to chat' : 'Profile'}
        </button>
        <button type="button" className="link" onClick={onLogout}>
          Sign out
        </button>
      </header>

      <div className="app-body">
        <aside className="sidebar">
          <div className="tabs">
            <button
              type="button"
              className={sidebarTab === 'chats' ? 'active' : ''}
              onClick={() => setSidebarTab('chats')}
            >
              Chats
            </button>
            <button
              type="button"
              className={sidebarTab === 'contacts' ? 'active' : ''}
              onClick={() => setSidebarTab('contacts')}
            >
              Contacts
            </button>
          </div>

          {sidebarTab === 'chats' ? <ChatList /> : <ContactList />}
        </aside>

        <main className="conversation">
          {showProfile && <Profile onClose={() => setShowProfile(false)} />}

          {!showProfile && !activeChatId && (
            <div className="placeholder">Pick a conversation, or search for someone.</div>
          )}

          {!showProfile && activeChatId && (
            <>
              <div className="conversation-header">
                {!activeChat?.group && activeChat?.otherUserId && (
                  <PresenceDot presence={presence[activeChat.otherUserId]} />
                )}
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
