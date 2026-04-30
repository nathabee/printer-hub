const printerGrid = document.getElementById("printerGrid");
const printerCount = document.getElementById("printerCount");
const enabledPrinterCount = document.getElementById("enabledPrinterCount");
const disabledPrinterCount = document.getElementById("disabledPrinterCount");
const lastRefresh = document.getElementById("lastRefresh");
const refreshButton = document.getElementById("refreshButton");

const printerConfigForm = document.getElementById("printerConfigForm");
const configuredPrinterList = document.getElementById("configuredPrinterList");
const clearPrinterFormButton = document.getElementById("clearPrinterFormButton");

const printerIdInput = document.getElementById("printerIdInput");
const printerNameInput = document.getElementById("printerNameInput");
const printerPortInput = document.getElementById("printerPortInput");
const printerModeInput = document.getElementById("printerModeInput");

const monitoringRulesForm = document.getElementById("monitoringRulesForm");
const pollIntervalSecondsInput = document.getElementById("pollIntervalSecondsInput");
const snapshotMinimumIntervalSecondsInput = document.getElementById("snapshotMinimumIntervalSecondsInput");
const temperatureDeltaThresholdInput = document.getElementById("temperatureDeltaThresholdInput");
const eventDeduplicationWindowSecondsInput = document.getElementById("eventDeduplicationWindowSecondsInput");
const errorPersistenceBehaviorInput = document.getElementById("errorPersistenceBehaviorInput");
const adminMessage = document.getElementById("adminMessage");
const printerEventState = new Map();
const printerCommandResultState = new Map();

async function loadDashboard() {
  await Promise.all([
    loadPrinters(),
    loadConfiguredPrinters(),
    loadMonitoringRules()
  ]);
}

async function loadPrinters() {
  try {
    const response = await fetch("/printers");

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    const data = await response.json();
    const printers = Array.isArray(data.printers) ? data.printers : [];

    const enabledPrinters = printers.filter((printer) => printer.enabled);
    const disabledPrinters = printers.filter((printer) => !printer.enabled);

    printerCount.textContent = String(printers.length);
    enabledPrinterCount.textContent = String(enabledPrinters.length);
    disabledPrinterCount.textContent = String(disabledPrinters.length);
    lastRefresh.textContent = new Date().toLocaleTimeString();

    renderPrinters(printers);
  } catch (error) {
    printerGrid.innerHTML = `<p class="muted">Failed to load printer data: ${escapeHtml(error.message)}</p>`;
  }
}

async function loadConfiguredPrinters() {
  try {
    const response = await fetch("/printers");

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    const data = await response.json();
    const printers = Array.isArray(data.printers) ? data.printers : [];

    renderConfiguredPrinters(printers);
  } catch (error) {
    configuredPrinterList.innerHTML = `<p class="muted">Failed to load printer configuration: ${escapeHtml(error.message)}</p>`;
  }
}

async function loadMonitoringRules() {
  try {
    const response = await fetch("/settings/monitoring");

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    const rules = await response.json();

    pollIntervalSecondsInput.value = rules.pollIntervalSeconds ?? 5;
    snapshotMinimumIntervalSecondsInput.value = rules.snapshotMinimumIntervalSeconds ?? 30;
    temperatureDeltaThresholdInput.value = rules.temperatureDeltaThreshold ?? 1.0;
    eventDeduplicationWindowSecondsInput.value = rules.eventDeduplicationWindowSeconds ?? 60;
    errorPersistenceBehaviorInput.value = rules.errorPersistenceBehavior ?? "DEDUPLICATED";
  } catch (error) {
    showAdminMessage(`Failed to load monitoring rules: ${error.message}`);
  }
}

async function savePrinter(event) {
  event.preventDefault();

  const printer = {
    id: printerIdInput.value.trim(),
    displayName: printerNameInput.value.trim(),
    portName: printerPortInput.value.trim(),
    mode: printerModeInput.value.trim(),
    enabled: true
  };

  const response = await fetch("/printers");
  const data = await response.json();
  const printers = Array.isArray(data.printers) ? data.printers : [];
  const exists = printers.some((item) => item.id === printer.id);

  const url = exists
    ? `/printers/${encodeURIComponent(printer.id)}`
    : "/printers";

  const method = exists ? "PUT" : "POST";

  try {
    const saveResponse = await fetch(url, {
      method,
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(printer)
    });

    if (!saveResponse.ok) {
      throw new Error(`HTTP ${saveResponse.status}`);
    }

    showAdminMessage(`Saved printer ${printer.id}.`);
    clearPrinterForm();

    await Promise.all([
      loadPrinters(),
      loadConfiguredPrinters()
    ]);
  } catch (error) {
    showAdminMessage(`Failed to save printer: ${error.message}`);
  }
}

async function saveMonitoringRules(event) {
  event.preventDefault();

  const rules = {
    pollIntervalSeconds: Number.parseInt(pollIntervalSecondsInput.value, 10),
    snapshotMinimumIntervalSeconds: Number.parseInt(snapshotMinimumIntervalSecondsInput.value, 10),
    temperatureDeltaThreshold: Number.parseFloat(temperatureDeltaThresholdInput.value),
    eventDeduplicationWindowSeconds: Number.parseInt(eventDeduplicationWindowSecondsInput.value, 10),
    errorPersistenceBehavior: errorPersistenceBehaviorInput.value
  };

  try {
    const response = await fetch("/settings/monitoring", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(rules)
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    await loadMonitoringRules();
    showAdminMessage("Saved monitoring rules.");
  } catch (error) {
    showAdminMessage(`Failed to save monitoring rules: ${error.message}`);
  }
}

async function handleConfiguredPrinterClick(event) {
  const button = event.target.closest("button[data-action]");

  if (!button) {
    return;
  }

  const action = button.dataset.action;
  const printerId = button.dataset.id;

  if (action === "edit") {
    await fillPrinterForm(printerId);
    return;
  }

  if (action === "enable" || action === "disable") {
    await updatePrinterEnabled(printerId, action);
    return;
  }

  if (action === "delete") {
    await deletePrinter(printerId);
  }
}

async function handlePrinterGridClick(event) {
  const button = event.target.closest("button[data-command], button[data-action]");

  if (!button) {
    return;
  }

  const printerId = button.dataset.id;

  if (!printerId) {
    return;
  }

  if (button.dataset.action === "load-events") {
    await loadPrinterEvents(printerId);
    return;
  }

  const command = button.dataset.command;

  if (command) {
    await executePrinterCommand(printerId, command);
  }
}

 
async function executePrinterCommand(printerId, command) {
  const resultElement = document.getElementById(`command-result-${cssSafeId(printerId)}`);
  const runningMessage = `Running ${command}...`;

  printerCommandResultState.set(printerId, runningMessage);

  if (resultElement) {
    resultElement.textContent = runningMessage;
  }

  const payload = { command };


  try {
    const response = await fetch(`/printers/${encodeURIComponent(printerId)}/commands`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    const responseBody = await response.json();

    if (!response.ok) {
      throw new Error(responseBody.error || `HTTP ${response.status}`);
    }

    const printerResponse = responseBody.response ?? "no response";
    const successMessage = `${responseBody.sentCommand}: ${printerResponse}`;

    printerCommandResultState.set(printerId, successMessage);

    if (resultElement) {
      resultElement.textContent = successMessage;
    }

    showAdminMessage(`Executed ${responseBody.sentCommand} on ${printerId}.`);

    await Promise.all([
      loadPrinters(),
      loadPrinterEvents(printerId)
    ]);
  } catch (error) {
    const failureMessage = `Command failed: ${error.message}`;

    printerCommandResultState.set(printerId, failureMessage);

    if (resultElement) {
      resultElement.textContent = failureMessage;
    }

    showAdminMessage(`Failed to execute ${command} on ${printerId}: ${error.message}`);

    await Promise.all([
      loadPrinters(),
      loadPrinterEvents(printerId)
    ]);
  }
}

async function loadPrinterEvents(printerId) {
  const eventsElement = document.getElementById(`printer-events-${cssSafeId(printerId)}`);

  if (!eventsElement) {
    return;
  }

  const loadingHtml = `<p class="muted">Loading events...</p>`;

  printerEventState.set(printerId, {
    isLoaded: false,
    html: loadingHtml
  });

  eventsElement.innerHTML = loadingHtml;

  try {
    const response = await fetch(`/printers/${encodeURIComponent(printerId)}/events`);

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    const data = await response.json();
    const events = Array.isArray(data.events) ? data.events : [];

    let html;

    if (events.length === 0) {
      html = `<p class="muted">No events recorded yet.</p>`;
    } else {
      const recentEvents = events.slice(0, 5);
      html = recentEvents.map(renderPrinterEvent).join("");
    }

    printerEventState.set(printerId, {
      isLoaded: true,
      html
    });

    eventsElement.innerHTML = html;
  } catch (error) {
    const html = `<p class="muted">Failed to load events: ${escapeHtml(error.message)}</p>`;

    printerEventState.set(printerId, {
      isLoaded: true,
      html
    });

    eventsElement.innerHTML = html;
  }
}

function renderPrinterEvent(event) {
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

function renderPrinters(printers) {
  if (printers.length === 0) {
    printerGrid.innerHTML = `<p class="muted">No configured printers found.</p>`;
    return;
  }

  printerGrid.innerHTML = printers.map(renderPrinterCard).join("");
}

function renderPrinterCard(printer) {
  const state = String(printer.state || "UNKNOWN");
  const stateClass = resolveStateClass(printer);
  const enabledBadge = printer.enabled
    ? `<span class="badge badge-enabled">enabled</span>`
    : `<span class="badge badge-disabled">disabled</span>`;
  const modeBadge = isSimulatedMode(printer.mode)
    ? `<span class="badge badge-simulated">simulated</span>`
    : `<span class="badge badge-real">real</span>`;

  const safeId = cssSafeId(printer.id);
  const disabledAttribute = printer.enabled ? "" : "disabled";

  const eventState = printerEventState.get(printer.id);
  const eventsHtml = eventState && eventState.isLoaded
    ? eventState.html
    : `<p class="muted">Events not loaded yet.</p>`;

  const commandResult = printerCommandResultState.get(printer.id)
    ?? "No manual command executed yet.";

  return `
    <article class="printer-card ${printer.enabled ? "" : "printer-card-disabled"}">
      <div class="card-header">
        <div>
          <h3>${escapeHtml(printer.displayName || printer.name || printer.id)}</h3>
          <p class="meta">${escapeHtml(printer.id)} · ${escapeHtml(printer.portName || "n/a")}</p>
        </div>
        <div class="card-badges">
          ${enabledBadge}
          ${modeBadge}
        </div>
      </div>

      <div class="row">
        <span>Status</span>
        <span class="badge ${stateClass}">${escapeHtml(renderStatusLabel(printer, state))}</span>
      </div>
      <div class="row">
        <span>Mode</span>
        <strong>${escapeHtml(printer.mode || "n/a")}</strong>
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
        <span>Updated</span>
        <strong>${escapeHtml(printer.updatedAt || "n/a")}</strong>
      </div>
      <div class="message-block">
        <span class="message-label">Last response</span>
        <div class="message-value">${escapeHtml(printer.lastResponse || "n/a")}</div>
      </div>
      <div class="message-block">
        <span class="message-label">Error</span>
        <div class="message-value">${escapeHtml(printer.errorMessage || "none")}</div>
      </div>

      <div class="command-section">
        <h4>Manual commands</h4>

        <div class="command-button-grid">
          <button type="button" data-id="${escapeHtml(printer.id)}" data-command="M105" ${disabledAttribute}>Read temp</button>
          <button type="button" data-id="${escapeHtml(printer.id)}" data-command="M114" ${disabledAttribute}>Read position</button>
          <button type="button" data-id="${escapeHtml(printer.id)}" data-command="M115" ${disabledAttribute}>Read firmware</button>
        </div>
 

        <div id="command-result-${safeId}" class="command-result muted">${escapeHtml(commandResult)}</div>
      </div>

      <div class="events-section">
        <div class="events-header">
          <h4>Recent events</h4>
          <button type="button" class="secondary-button small-button" data-action="load-events" data-id="${escapeHtml(printer.id)}">Load events</button>
        </div>
        <div id="printer-events-${safeId}" class="events-list">
          ${eventsHtml}
        </div>
      </div>
    </article>
  `;
}

function renderConfiguredPrinters(printers) {
  if (printers.length === 0) {
    configuredPrinterList.innerHTML = `<p class="muted">No configured printers found.</p>`;
    return;
  }

  configuredPrinterList.innerHTML = printers.map(renderConfiguredPrinter).join("");
}

function renderConfiguredPrinter(printer) {
  const enabledBadge = printer.enabled
    ? `<span class="badge badge-enabled">enabled</span>`
    : `<span class="badge badge-disabled">disabled</span>`;
  const modeBadge = isSimulatedMode(printer.mode)
    ? `<span class="badge badge-simulated">simulated</span>`
    : `<span class="badge badge-real">real</span>`;

  return `
    <article class="config-card">
      <div>
        <h3>${escapeHtml(printer.displayName || printer.name || printer.id)}</h3>
        <p class="meta">${escapeHtml(printer.id)} · ${escapeHtml(printer.portName || "n/a")} · ${escapeHtml(printer.mode || "n/a")}</p>
        <div class="card-badges">
          ${enabledBadge}
          ${modeBadge}
        </div>
      </div>

      <div class="config-actions">
        <button type="button" class="secondary-button" data-action="edit" data-id="${escapeHtml(printer.id)}">Edit</button>
        <button type="button" class="secondary-button" data-action="enable" data-id="${escapeHtml(printer.id)}">Enable</button>
        <button type="button" class="secondary-button" data-action="disable" data-id="${escapeHtml(printer.id)}">Disable</button>
        <button type="button" class="danger-button" data-action="delete" data-id="${escapeHtml(printer.id)}">Delete</button>
      </div>
    </article>
  `;
}

async function fillPrinterForm(printerId) {
  const response = await fetch("/printers");

  if (!response.ok) {
    showAdminMessage(`Failed to load printer for edit: HTTP ${response.status}`);
    return;
  }

  const data = await response.json();
  const printers = Array.isArray(data.printers) ? data.printers : [];
  const printer = printers.find((item) => item.id === printerId);

  if (!printer) {
    showAdminMessage(`Printer not found: ${printerId}`);
    return;
  }

  printerIdInput.value = printer.id || "";
  printerNameInput.value = printer.displayName || printer.name || "";
  printerPortInput.value = printer.portName || "";
  printerModeInput.value = printer.mode || "sim";
}

async function deletePrinter(printerId) {
  try {
    const response = await fetch(`/printers/${encodeURIComponent(printerId)}`, {
      method: "DELETE"
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    showAdminMessage(`Deleted printer ${printerId}.`);

    await Promise.all([
      loadPrinters(),
      loadConfiguredPrinters()
    ]);
  } catch (error) {
    showAdminMessage(`Failed to delete printer: ${error.message}`);
  }
}

async function updatePrinterEnabled(printerId, action) {
  try {
    const response = await fetch(`/printers/${encodeURIComponent(printerId)}/${action}`, {
      method: "POST"
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    showAdminMessage(`${action === "enable" ? "Enabled" : "Disabled"} printer ${printerId}.`);

    await Promise.all([
      loadPrinters(),
      loadConfiguredPrinters()
    ]);
  } catch (error) {
    showAdminMessage(`Failed to ${action} printer: ${error.message}`);
  }
}

function resolveStateClass(printer) {
  if (!printer.enabled) {
    return "status-disabled";
  }

  const state = String(printer.state || "UNKNOWN").toLowerCase();

  if (state === "error" || state === "disconnected") {
    return "status-error";
  }

  if (state === "connecting" || state === "heating" || state === "printing") {
    return "status-warn";
  }

  if (state === "idle") {
    return "status-ok";
  }

  return "status-unknown";
}

function renderStatusLabel(printer, state) {
  if (!printer.enabled) {
    return "DISABLED";
  }

  return state;
}

function isSimulatedMode(mode) {
  const normalized = String(mode || "").toLowerCase();
  return normalized === "sim"
    || normalized === "simulated"
    || normalized === "sim-error"
    || normalized === "sim-timeout"
    || normalized === "sim-disconnected";
}

function cssSafeId(value) {
  return String(value).replaceAll(/[^a-zA-Z0-9_-]/g, "_");
}

function clearPrinterForm() {
  printerConfigForm.reset();
  printerModeInput.value = "real";
}

function showAdminMessage(message) {
  adminMessage.textContent = message;
}

function formatTemperature(value) {
  if (value === null || value === undefined) {
    return "n/a";
  }

  return `${Number(value).toFixed(1)} °C`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#039;");
}

refreshButton.addEventListener("click", loadDashboard);
printerConfigForm.addEventListener("submit", savePrinter);
monitoringRulesForm.addEventListener("submit", saveMonitoringRules);
configuredPrinterList.addEventListener("click", handleConfiguredPrinterClick);
printerGrid.addEventListener("click", handlePrinterGridClick); 
clearPrinterFormButton.addEventListener("click", clearPrinterForm);

loadDashboard();
setInterval(loadPrinters, 3000);