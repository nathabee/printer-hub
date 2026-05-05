import { escapeHtml } from "../app.js";

export function renderEventList(events, emptyMessage) {
  if (!events || events.length === 0) {
    return `<p class="muted">${escapeHtml(emptyMessage)}</p>`;
  }

  return events.map((event) => renderEventItem(event)).join("");
}

export function renderEventItem(event) {
  return `
    <div class="event-item">
      <div class="event-header">
        <strong>${escapeHtml(event.eventType || "UNKNOWN")}</strong>
        <span class="event-time">${escapeHtml(event.createdAt || "n/a")}</span>
      </div>
      <div class="event-message">${escapeHtml(event.message || "none")}</div>
    </div>
  `;
}
