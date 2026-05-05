import { renderEventList } from "../components/event-list.js";
import { renderPlaceholderCard } from "../components/placeholder-card.js";
import { renderPrinterStatusPanel } from "../components/status-panels.js";
import { state } from "../state.js";

export function renderPrinterHome(printer) {
  const printerEvents = state.printerEvents.get(printer.id) ?? [];

  return `
    ${renderPrinterStatusPanel(printer)}

    <section class="two-column-grid">
      <article class="panel-card">
        <div class="section-header compact">
          <div>
            <h3>Recent printer events</h3>
            <p class="muted">Most recent operational events for the selected printer.</p>
          </div>
          <button type="button" class="secondary-button small-button" data-load-printer-events="${printer.id}">Load events</button>
        </div>
        <div class="events-list">
          ${renderEventList(printerEvents.slice(0, 6), "Events not loaded yet.")}
        </div>
      </article>

      ${renderPlaceholderCard(
        "Quick print context",
        "Reserved for future job-centric print status on the printer home page.",
        [
          "Active piece or job summary",
          "Progress and completion estimate",
          "Current job phase"
        ]
      )}
    </section>
  `;
}
