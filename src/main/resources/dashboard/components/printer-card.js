import { escapeHtml, formatTemperature, isSimulatedMode, renderStatusLabel, resolveStateClass } from "../app.js";

export function renderPrinterCard(printer, options = {}) {
  const actions = options.actions ?? [];
  const isSelected = options.isSelected === true;

  const badges = [
    printer.enabled
      ? `<span class="badge badge-enabled">enabled</span>`
      : `<span class="badge badge-disabled">disabled</span>`,
    isSimulatedMode(printer.mode)
      ? `<span class="badge badge-simulated">simulated</span>`
      : `<span class="badge badge-real">real</span>`,
    isSelected
      ? `<span class="badge badge-selected">selected</span>`
      : ""
  ].filter(Boolean);

  return `
    <article class="printer-card ${printer.enabled ? "" : "printer-card-disabled"} ${isSelected ? "printer-card-selected" : ""}">
      <div class="card-header">
        <div>
          <h3>${escapeHtml(printer.displayName || printer.name || printer.id)}</h3>
          <p class="meta">${escapeHtml(printer.id)} · ${escapeHtml(printer.portName || "n/a")}</p>
        </div>
        <div class="card-badges">${badges.join("")}</div>
      </div>

      <div class="row">
        <span>Status</span>
        <span class="badge ${resolveStateClass(printer)}">${escapeHtml(renderStatusLabel(printer, printer.state || "UNKNOWN"))}</span>
      </div>
      <div class="row">
        <span>Hotend</span>
        <strong>${formatTemperature(printer.hotendTemperature)}</strong>
      </div>
      <div class="row">
        <span>Bed</span>
        <strong>${formatTemperature(printer.bedTemperature)}</strong>
      </div>
      <div class="row">
        <span>Mode</span>
        <strong>${escapeHtml(printer.mode || "n/a")}</strong>
      </div>
      <div class="row">
        <span>Updated</span>
        <strong>${escapeHtml(printer.updatedAt || "n/a")}</strong>
      </div>

      <div class="message-block">
        <span class="message-label">Last response</span>
        <div class="message-value">${escapeHtml(printer.lastResponse || "n/a")}</div>
      </div>
      <div class="message-block">
        <span class="message-label">Current error</span>
        <div class="message-value">${escapeHtml(printer.errorMessage || "none")}</div>
      </div>

      ${actions.length > 0 ? `
        <div class="action-row">
          ${actions.join("")}
        </div>
      ` : ""}
    </article>
  `;
}