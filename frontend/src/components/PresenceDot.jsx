const relative = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

const UNITS = [
  ['day', 86_400_000],
  ['hour', 3_600_000],
  ['minute', 60_000],
];

/** "3 hours ago" from an ISO timestamp, coarsest unit that fits. */
function lastSeenLabel(lastSeen) {
  if (!lastSeen) {
    return 'Offline';
  }

  const elapsed = Date.now() - new Date(lastSeen).getTime();
  const [unit, ms] = UNITS.find(([, size]) => elapsed >= size) ?? [];

  return unit ? `Last seen ${relative.format(-Math.floor(elapsed / ms), unit)}` : 'Last seen just now';
}

/**
 * Presence is derived from the WebSocket session, never stored as a column:
 * a server that crashes cannot leave anyone stuck showing "online", because
 * the presence key expires on its own.
 */
export default function PresenceDot({ presence }) {
  const online = Boolean(presence?.online);

  return (
    <span
      className={`presence-dot ${online ? 'online' : 'offline'}`}
      title={online ? 'Online' : lastSeenLabel(presence?.lastSeen)}
      aria-label={online ? 'Online' : 'Offline'}
    />
  );
}
