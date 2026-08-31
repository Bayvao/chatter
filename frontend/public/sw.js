/* eslint-env serviceworker */

/**
 * Web Push service worker.
 *
 * The browser runs this even when no Chatter tab is open — that is the whole
 * point of Web Push, and why no third-party messaging account (FCM, APNs) is
 * involved: the push service the browser nominated delivers the encrypted
 * payload straight here.
 */

const NOTIFICATION_TAG_PREFIX = 'chatter-chat-';

self.addEventListener('install', () => {
  // Take over immediately rather than waiting for every old tab to close;
  // there is no cached app shell whose version could disagree.
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('push', (event) => {
  // A push with no payload is still worth surfacing — treat it as a generic
  // nudge rather than dropping it.
  let payload = {};
  if (event.data) {
    try {
      payload = event.data.json();
    } catch {
      payload = { body: event.data.text() };
    }
  }

  const title = payload.title || 'Chatter';
  event.waitUntil(
    self.registration.showNotification(title, {
      body: payload.body || 'New message',
      icon: '/icon-192.png',
      badge: '/icon-192.png',
      // One notification per chat: a burst of messages replaces rather than
      // stacks, so the tray does not fill up with the same conversation.
      tag: payload.chatId ? NOTIFICATION_TAG_PREFIX + payload.chatId : undefined,
      renotify: Boolean(payload.chatId),
      data: { chatId: payload.chatId },
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  const chatId = event.notification.data?.chatId;
  const target = chatId ? `/chat?chatId=${chatId}` : '/chat';

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
      // Reuse an open tab if there is one; opening a second Chatter window
      // on every notification is hostile.
      for (const client of clients) {
        if (client.url.includes('/chat') && 'focus' in client) {
          client.postMessage({ type: 'notification-click', chatId });
          return client.focus();
        }
      }
      return self.clients.openWindow(target);
    }),
  );
});
