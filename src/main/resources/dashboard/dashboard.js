const printerGrid = document.getElementById("printerGrid");
const printerCount = document.getElementById("printerCount");
const lastRefresh = document.getElementById("lastRefresh");
const refreshButton = document.getElementById("refreshButton");

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

function renderPrinters(printers) {
  if (printers.length === 0) {
    printerGrid.innerHTML = `<p class="muted">No printers available.</p>`;
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
      <p class="meta">${escapeHtml(printer.id)} · ${escapeHtml(printer.portName || "n/a")}</p>

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

refreshButton.addEventListener("click", loadPrinters);

loadPrinters();
setInterval(loadPrinters, 3000);