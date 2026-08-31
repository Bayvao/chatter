import api from './api';

/**
 * Web Push (RFC 8030/8291) subscription flow.
 *
 * The browser picks its own push service and hands us an endpoint; we sign
 * deliveries to it with a VAPID key. No third-party messaging account is
 * involved, and nothing here is specific to any vendor.
 *
 * Push only works over HTTPS, or on localhost. On plain http:// elsewhere
 * `navigator.serviceWorker` is simply absent and we no-op.
 */

/**
 * The server sends the VAPID public key base64url-encoded, but
 * `pushManager.subscribe` insists on raw bytes.
 */
function urlBase64ToUint8Array(base64) {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4);
  const normalized = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(normalized);

  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}

/** Flattens the browser's nested `keys` object into the shape the API takes. */
function toRequest(subscription) {
  const { endpoint, keys } = subscription.toJSON();
  return { endpoint, p256dh: keys.p256dh, auth: keys.auth };
}

/**
 * Best effort throughout: a browser without push, a server with push turned
 * off, or a user who declines the permission prompt all end the same way —
 * a warning in the console and a working app without notifications.
 *
 * @returns {Promise<boolean>} whether a subscription is now registered
 */
export async function enablePush() {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
    return false;
  }

  try {
    const { data } = await api.get('/api/push/public-key');
    if (!data.enabled || !data.publicKey) {
      return false;
    }

    if (Notification.permission === 'denied') {
      return false;
    }
    if (Notification.permission === 'default' && (await Notification.requestPermission()) !== 'granted') {
      return false;
    }

    const registration = await navigator.serviceWorker.register('/sw.js');
    await navigator.serviceWorker.ready;

    // Reuse an existing subscription when the key still matches; re-subscribing
    // with a different key throws, so drop the stale one first.
    let subscription = await registration.pushManager.getSubscription();
    if (subscription) {
      await subscription.unsubscribe();
    }

    subscription = await registration.pushManager.subscribe({
      // Required by Chrome: we promise every push produces a visible
      // notification, which sw.js honours.
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(data.publicKey),
    });

    await api.post('/api/push/subscriptions', toRequest(subscription));
    return true;
  } catch (error) {
    console.warn('Push notifications unavailable:', error?.message ?? error);
    return false;
  }
}

/** Called on sign-out, so the next user of this browser is not notified for us. */
export async function disablePush() {
  if (!('serviceWorker' in navigator)) {
    return;
  }

  try {
    const registration = await navigator.serviceWorker.getRegistration();
    const subscription = await registration?.pushManager.getSubscription();
    if (!subscription) {
      return;
    }

    // Tell the server before unsubscribing: afterwards we no longer have the
    // endpoint to identify the row by.
    await api.delete('/api/push/subscriptions', { data: toRequest(subscription) });
    await subscription.unsubscribe();
  } catch (error) {
    console.warn('Could not remove push subscription:', error?.message ?? error);
  }
}
