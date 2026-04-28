const printerGrid = document.getElementById("printerGrid");
const printerCount = document.getElementById("printerCount");
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
const snapshotOnStateChangeInput = document.getElementById("snapshotOnStateChangeInput");
const temperatureThresholdInput = document.getElementById("temperatureThresholdInput");
const minIntervalSecondsInput = document.getElementById("minIntervalSecondsInput");
const adminMessage = document.getElementById("adminMessage");

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

    printerCount.textContent = String(printers.length);
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
  return;
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
  showAdminMessage("Monitoring rules are not active yet.");
  return;
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


function renderPrinters(printers) {
  if (printers.length === 0) {
    printerGrid.innerHTML = `<p class="muted">No enabled printers available.</p>`;
    return;
  }

  printerGrid.innerHTML = printers.map(renderPrinterCard).join("");
}

function renderPrinterCard(printer) {
  const state = String(printer.state || "UNKNOWN");
  const stateClass = `state-${state.toLowerCase()}`;

  return `
    <article class="printer-card">
      <h3>${escapeHtml(printer.name || printer.id)}</h3>
      <p class="meta">${escapeHtml(printer.id)} · ${escapeHtml(printer.portName || "n/a")} · ${escapeHtml(printer.mode || "n/a")}</p>

      <div class="row">
        <span>State</span>
        <span class="badge ${stateClass}">${escapeHtml(state)}</span>
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
        <span>Assigned job</span>
        <strong>${escapeHtml(printer.assignedJobId || "none")}</strong>
      </div>
      <div class="row">
        <span>Updated</span>
        <strong>${escapeHtml(printer.updatedAt || "n/a")}</strong>
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
  return `
    <article class="config-card">
      <div>
        <h3>${escapeHtml(printer.displayName || printer.name || printer.id)}</h3>
        <p class="meta">${escapeHtml(printer.id)} · ${escapeHtml(printer.portName || "n/a")} · ${escapeHtml(printer.mode || "n/a")}</p>
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
  printerModeInput.value = printer.mode || "simulated";
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
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

refreshButton.addEventListener("click", loadDashboard);
printerConfigForm.addEventListener("submit", savePrinter);
monitoringRulesForm.addEventListener("submit", saveMonitoringRules);
configuredPrinterList.addEventListener("click", handleConfiguredPrinterClick);
clearPrinterFormButton.addEventListener("click", clearPrinterForm);

loadDashboard();
setInterval(loadPrinters, 3000);