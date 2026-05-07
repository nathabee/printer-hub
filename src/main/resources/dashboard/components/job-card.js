import { escapeHtml } from "../dashboard.js";

export function renderJobCard(job, options = {}) {
  const eventsHtml = options.eventsHtml ?? `<p class="muted">Job history not loaded yet.</p>`;
  const executionStepsHtml = options.executionStepsHtml ?? `<p class="muted">Execution diagnostics not loaded yet.</p>`;
  const showActions = options.showActions ?? true;
  const historyOpen = options.historyOpen === true;
  const diagnosticsOpen = options.diagnosticsOpen === true;
  const canStart = job.state === "ASSIGNED";
  const canCancel = !["COMPLETED", "FAILED", "CANCELLED"].includes(job.state);

  return `
    <article class="job-card">
      <div class="job-header">
        <div>
          <div class="kicker">Job</div>
          <h3>${escapeHtml(job.name || job.id)}</h3>
          <p class="meta">${escapeHtml(job.id)} · ${escapeHtml(job.type || "n/a")} · ${escapeHtml(job.printerId || "unassigned")}</p>
        </div>
        <div class="badge-row">
          <span class="badge badge-real">${escapeHtml(job.state || "UNKNOWN")}</span>
        </div>
      </div>

      <div class="info-row">
        <span>Created</span>
        <strong>${escapeHtml(job.createdAt || "n/a")}</strong>
      </div>
      <div class="info-row">
        <span>Started</span>
        <strong>${escapeHtml(job.startedAt || "n/a")}</strong>
      </div>
      <div class="info-row">
        <span>Finished</span>
        <strong>${escapeHtml(job.finishedAt || "n/a")}</strong>
      </div>
      <div class="info-row">
        <span>Print file</span>
        <span class="inline-actions">
          <strong>${escapeHtml(job.printFileId || "none")}</strong>
          ${job.printFileId ? `<button type="button" class="secondary-button small-button" data-job-action="show-print-file" data-job-id="${escapeHtml(job.id)}">Show</button>` : ""}
        </span>
      </div>
      <div class="info-row">
        <span>Printer SD target</span>
        <strong>${escapeHtml(job.printerSdFileId || "none")}</strong>
      </div>

      <div class="message-block">
        <span class="message-label">Failure detail</span>
        <div class="message-value">${escapeHtml(job.failureDetail || job.failureReason || "none")}</div>
      </div>

      ${showActions ? `
        <div class="action-row">
          <button type="button" class="secondary-button" data-job-action="start" data-job-id="${escapeHtml(job.id)}" ${canStart ? "" : "disabled"}>Start</button>
          <button type="button" class="secondary-button" data-job-action="cancel" data-job-id="${escapeHtml(job.id)}" ${canCancel ? "" : "disabled"}>Cancel</button>
          <button type="button" class="secondary-button" data-job-action="load-events" data-job-id="${escapeHtml(job.id)}">Load history</button>
          <button type="button" class="secondary-button" data-job-action="load-execution-steps" data-job-id="${escapeHtml(job.id)}">Load diagnostics</button>
          <button type="button" class="danger-button" data-job-action="delete" data-job-id="${escapeHtml(job.id)}">Delete</button>
        </div>
      ` : ""}

      <details class="events-section" data-job-detail-section="history" data-job-id="${escapeHtml(job.id)}" ${historyOpen ? "open" : ""}>
        <summary class="events-header">
          <h4>Job history</h4>
        </summary>
        <div class="events-list">${eventsHtml}</div>
      </details>

      <details class="events-section" data-job-detail-section="diagnostics" data-job-id="${escapeHtml(job.id)}" ${diagnosticsOpen ? "open" : ""}>
        <summary class="events-header">
          <h4>Execution diagnostics</h4>
        </summary>
        <div class="events-list">${executionStepsHtml}</div>
      </details>
    </article>
  `;
}
