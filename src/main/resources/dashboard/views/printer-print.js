import { renderJobCard } from "../components/job-card.js";
import { renderPlaceholderCard } from "../components/placeholder-card.js";
import { escapeHtml } from "../app.js";
import { state } from "../state.js";

export function renderPrinterPrint(printer, jobsForPrinter) {
  const jobsHtml = jobsForPrinter.length === 0
    ? `<div class="empty-state"><h3>No jobs assigned</h3><p class="muted">Create a job below or leave placeholders visible for the later production workflow.</p></div>`
    : jobsForPrinter.map((job) => renderJobCard(job, {
        eventsHtml: renderJobEvents(job.id)
      })).join("");

  return `
    <section class="section-card">
      <div class="section-header">
        <div>
          <div class="kicker">Print</div>
          <h2>Jobs for ${escapeHtml(printer.displayName || printer.id)}</h2>
          <p class="lead">Current backend jobs stay visible here and this page is prepared to evolve toward piece-oriented production jobs later.</p>
        </div>
      </div>

      <form id="jobForm" class="form-grid">
        <label>
          Job name
          <input id="jobNameInput" name="jobName" type="text" placeholder="Read firmware on printer-1" required>
        </label>

        <label>
          Job type
          <select id="jobTypeInput" name="type" required>
            <option value="READ_FIRMWARE_INFO">READ_FIRMWARE_INFO</option>
            <option value="READ_TEMPERATURE">READ_TEMPERATURE</option>
            <option value="READ_POSITION">READ_POSITION</option>
            <option value="HOME_AXES">HOME_AXES</option>
            <option value="SET_NOZZLE_TEMPERATURE">SET_NOZZLE_TEMPERATURE</option>
            <option value="SET_BED_TEMPERATURE">SET_BED_TEMPERATURE</option>
            <option value="SET_FAN_SPEED">SET_FAN_SPEED</option>
            <option value="TURN_FAN_OFF">TURN_FAN_OFF</option>
          </select>
        </label>

        <label>
          Printer
          <select id="jobPrinterIdInput" name="printerId">
            ${buildPrinterOptions(printer.id)}
          </select>
        </label>

        <label>
          Target temperature
          <input id="jobTargetTemperatureInput" name="targetTemperature" type="number" step="1" min="0" placeholder="200">
        </label>

        <label>
          Fan speed
          <input id="jobFanSpeedInput" name="fanSpeed" type="number" step="1" min="0" max="255" placeholder="255">
        </label>

        <div class="form-actions">
          <button type="submit">Create job</button>
          <button id="clearJobFormButton" type="button" class="secondary-button">Clear form</button>
        </div>
      </form>
    </section>

    <section class="job-layout">${jobsHtml}</section>

    <section class="two-column-grid">
      ${renderPlaceholderCard(
        "Future production workflow",
        "Reserved for the later job model where one job produces a piece through several internal steps.",
        [
          "Model or plan file reference",
          "Preparation phase",
          "Execution phase",
          "Completion and piece traceability"
        ]
      )}
      ${renderPlaceholderCard(
        "Future print preview",
        "Reserved for later print-specific data that does not exist yet in the current backend.",
        [
          "Model preview",
          "Estimated duration",
          "Piece metadata",
          "Operator approval state"
        ]
      )}
    </section>
  `;
}

function renderJobEvents(jobId) {
  const events = state.jobEvents.get(jobId) ?? [];

  if (events.length === 0) {
    return `<p class="muted">Job history not loaded yet.</p>`;
  }

  return events.slice(0, 8).map((event) => `
    <div class="event-item">
      <div class="event-header">
        <strong>${escapeHtml(event.eventType || "UNKNOWN")}</strong>
        <span class="event-time">${escapeHtml(event.createdAt || "n/a")}</span>
      </div>
      <div class="event-message">${escapeHtml(event.message || "none")}</div>
    </div>
  `).join("");
}

function buildPrinterOptions(selectedPrinterId) {
  const printers = state.printers;

  return [
    `<option value="">Select printer</option>`,
    ...printers.map((printer) => `
      <option value="${escapeHtml(printer.id)}" ${printer.id === selectedPrinterId ? "selected" : ""}>
        ${escapeHtml(printer.displayName || printer.id)}
      </option>
    `)
  ].join("");
}
