import { useEffect } from 'react';
import { useChatStore } from '../store/chatStore';
import { useContactStore } from '../store/contactStore';

export default function ContactList() {
  const { openChatWith } = useChatStore();
  const { contacts, incoming, outgoing, loadAll, accept, decline, remove, block } = useContactStore();

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  return (
    <div className="contacts-panel">
      {/* Requests first: they need an answer, contacts do not. */}
      {incoming.length > 0 && (
        <section className="requests">
          <h3>Requests</h3>
          <ul>
            {incoming.map(({ id, user }) => (
              <li key={id}>
                <span className="contact-name">
                  {user.displayName}
                  <span className="muted"> @{user.username}</span>
                </span>
                <span className="contact-actions">
                  <button type="button" className="link" onClick={() => accept(user.id)}>
                    Accept
                  </button>
                  <button type="button" className="link" onClick={() => decline(user.id)}>
                    Decline
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {outgoing.length > 0 && (
        <section className="requests">
          <h3>Sent</h3>
          <ul>
            {outgoing.map(({ id, user }) => (
              <li key={id}>
                <span className="contact-name">
                  {user.displayName}
                  <span className="muted"> @{user.username}</span>
                </span>
                <span className="contact-actions">
                  {/* Same endpoint as declining: either party may end a pending request. */}
                  <button type="button" className="link" onClick={() => decline(user.id)}>
                    Cancel
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {contacts.length === 0 && incoming.length === 0 && outgoing.length === 0 && (
        <div className="empty">No contacts yet. Search for someone and send a request.</div>
      )}

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
    </div>
  );
}
