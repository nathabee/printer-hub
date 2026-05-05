async function requestJson(url, options = {}) {
  const response = await fetch(url, options);
  const body = await safeJson(response);

  if (!response.ok) {
    throw new Error(body.error || `HTTP ${response.status}`);
  }

  return body;
}

async function safeJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}

export async function getPrinters() {
  const data = await requestJson("/printers");
  return Array.isArray(data.printers) ? data.printers : [];
}

export async function createPrinter(printer) {
  return requestJson("/printers", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(printer)
  });
}

export async function updatePrinter(printerId, printer) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(printer)
  });
}

export async function deletePrinter(printerId) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}`, {
    method: "DELETE"
  });
}

export async function setPrinterEnabled(printerId, enabled) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/${enabled ? "enable" : "disable"}`, {
    method: "POST"
  });
}

export async function executePrinterCommand(printerId, command) {
  return requestJson(`/printers/${encodeURIComponent(printerId)}/commands`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ command })
  });
}

export async function getPrinterEvents(printerId) {
  const data = await requestJson(`/printers/${encodeURIComponent(printerId)}/events`);
  return Array.isArray(data.events) ? data.events : [];
}

export async function getMonitoringRules() {
  return requestJson("/settings/monitoring");
}

export async function saveMonitoringRules(rules) {
  return requestJson("/settings/monitoring", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(rules)
  });
}

export async function getJobs() {
  const data = await requestJson("/jobs");
  return Array.isArray(data.jobs) ? data.jobs : [];
}

export async function createJob(job) {
  return requestJson("/jobs", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(job)
  });
}

export async function startJob(jobId) {
  return requestJson(`/jobs/${encodeURIComponent(jobId)}/start`, {
    method: "POST"
  });
}

export async function cancelJob(jobId) {
  return requestJson(`/jobs/${encodeURIComponent(jobId)}/cancel`, {
    method: "POST"
  });
}

export async function getJobEvents(jobId) {
  const data = await requestJson(`/jobs/${encodeURIComponent(jobId)}/events`);
  return Array.isArray(data.events) ? data.events : [];
}
