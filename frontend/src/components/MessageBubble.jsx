const timeFormat = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' });

export default function MessageBubble({ message, own }) {
  return (
    <div className={`bubble-row ${own ? 'own' : ''}`}>
      <div className={`bubble ${own ? 'own' : ''}`}>
        {!own && <div className="bubble-sender">{message.senderName}</div>}
        <div className="bubble-content">
          {message.deleted ? <em>This message was deleted</em> : message.content}
        </div>
        <div className="bubble-meta">{timeFormat.format(new Date(message.createdAt))}</div>
      </div>
    </div>
  );
}
