const state = {
  farms: [],
  replayDetails: new Map(),
  selectedFrames: [],
  selectedFrameIndex: 0,
};

loadDashboard().catch(error => {
  document.getElementById('farm-cards').innerHTML = `<div class="notice">${escapeHtml(error.message)}</div>`;
});

async function loadDashboard() {
  const overview = await fetchJson('/api/central/farms/overview');
  state.farms = overview.farms || [];
  document.getElementById('last-refresh').textContent = `Updated ${new Date().toLocaleTimeString()}`;
  renderSummary(state.farms);
  renderFarmTable(state.farms);
  await renderStructure(state.farms);
  await renderReplays(state.farms);
}

function renderSummary(farms) {
  const totals = farms.reduce((acc, item) => {
    const farm = item.farm;
    acc.farms += 1;
    acc.printers += farm.printerCount || 0;
    acc.cameras += farm.cameraCount || 0;
    acc.active += farm.activePrintCount || 0;
    acc.warnings += farm.warningCount || 0;
    acc.errors += farm.errorCount || 0;
    acc.alerts += farm.spaghettiAlertCount || 0;
    acc[item.status.toLowerCase()] = (acc[item.status.toLowerCase()] || 0) + 1;
    return acc;
  }, { farms: 0, printers: 0, cameras: 0, active: 0, warnings: 0, errors: 0, alerts: 0 });

  document.getElementById('farm-cards').innerHTML = [
    metricCard('Farms', totals.farms, `${totals.online || 0} online`),
    metricCard('Printers', totals.printers, `${totals.active} active prints`),
    metricCard('Cameras', totals.cameras, 'pushed structure'),
    metricCard('Warnings', totals.warnings, `${totals.errors} errors`),
    metricCard('Spaghetti Alerts', totals.alerts, 'latest heartbeat'),
  ].join('');
}

function metricCard(label, value, detail) {
  return `<article class="metric-card"><span>${escapeHtml(label)}</span><strong>${value}</strong><small>${escapeHtml(detail)}</small></article>`;
}

function renderFarmTable(farms) {
  const rows = farms.map(item => {
    const farm = item.farm;
    return `<tr>
      <td>${escapeHtml(farm.farmName)}</td>
      <td><span class="status status-${escapeAttribute(item.status.toLowerCase())}">${escapeHtml(item.status)}</span></td>
      <td>${farm.printerCount}</td>
      <td>${farm.cameraCount}</td>
      <td>${farm.activePrintCount}</td>
      <td>${farm.warningCount}</td>
      <td>${farm.errorCount}</td>
      <td>${farm.spaghettiAlertCount}</td>
      <td>${escapeHtml(formatTime(farm.lastSeenAt))}</td>
    </tr>`;
  }).join('') || '<tr><td colspan="9">No farms registered.</td></tr>';
  document.getElementById('farms').innerHTML = rows;
}

async function renderStructure(farms) {
  const cards = [];
  for (const item of farms) {
    const farm = item.farm;
    const data = await fetchJson(`/api/central/farms/${encodeURIComponent(farm.farmId)}/structure`);
    const structure = data.structure || {};
    const printers = Array.isArray(structure.printers) ? structure.printers : [];
    const cameras = Array.isArray(structure.cameras) ? structure.cameras : [];
    cards.push(`<article class="structure-card">
      <h3>${escapeHtml(farm.farmName)}</h3>
      <p>${escapeHtml(formatTime(data.structureUpdatedAt) || 'No structure pushed')}</p>
      <div class="columns">
        <div>
          <h4>Printers</h4>
          ${renderStructureItems(printers, 'printerId')}
        </div>
        <div>
          <h4>Cameras</h4>
          ${renderStructureItems(cameras, 'cameraId')}
        </div>
      </div>
    </article>`);
  }
  document.getElementById('structure-view').innerHTML = cards.join('') || '<p class="empty">No farm structure available.</p>';
}

function renderStructureItems(items, idField) {
  if (items.length === 0) {
    return '<p class="empty compact">None pushed.</p>';
  }
  return `<ul>${items.map(item => `<li><strong>${escapeHtml(item.displayName || item[idField])}</strong><span>${escapeHtml(item.status || (item.enabled ? 'ENABLED' : 'DISABLED'))}</span></li>`).join('')}</ul>`;
}

async function renderReplays(farms) {
  const replayItems = [];
  for (const item of farms) {
    const farm = item.farm;
    const data = await fetchJson(`/api/central/farms/${encodeURIComponent(farm.farmId)}/camera-replay-packages`);
    for (const replay of data.packages || []) {
      replayItems.push({ farm, replay });
    }
  }

  if (replayItems.length === 0) {
    document.getElementById('replays').innerHTML = '<p class="empty">No replay packages uploaded.</p>';
    return;
  }

  const items = await Promise.all(replayItems.map(async ({ farm, replay }) => {
    const detail = await fetchJson(`/api/central/camera-replay-packages/${encodeURIComponent(replay.packageId)}`);
    state.replayDetails.set(replay.packageId, detail);
    const frames = replayFrames(detail.files || []);
    return `<article class="replay-item">
      <h3>${escapeHtml(replay.label || replay.cameraJobId || replay.packageId)}</h3>
      <p>${escapeHtml(farm.farmName)} · ${escapeHtml(replay.cameraId || '')} · ${replay.frameCount} frames · ${escapeHtml(formatTime(replay.startedAt))}</p>
      <button type="button" class="link-button" data-replay="${escapeAttribute(replay.packageId)}">View Uploaded Frames</button>
      <div class="replay-links">${frames.slice(0, 6).map(file => `<a href="${escapeAttribute(file.url)}" target="_blank" rel="noopener">${escapeHtml(file.relativePath)}</a>`).join('')}</div>
    </article>`;
  }));

  document.getElementById('replays').innerHTML = items.join('');
  document.querySelectorAll('[data-replay]').forEach(button => {
    button.addEventListener('click', () => showReplay(button.dataset.replay));
  });
}

function showReplay(packageId) {
  const detail = state.replayDetails.get(packageId);
  state.selectedFrames = replayFrames(detail.files || []);
  state.selectedFrameIndex = 0;
  renderFrame();
}

function renderFrame() {
  const stage = document.getElementById('replay-frame');
  const meta = document.getElementById('replay-meta');
  if (state.selectedFrames.length === 0) {
    stage.textContent = 'No replayable frames.';
    meta.innerHTML = '';
    return;
  }
  const frame = state.selectedFrames[state.selectedFrameIndex];
  stage.innerHTML = `<img src="${escapeAttribute(frame.url)}" alt="${escapeAttribute(frame.relativePath)}">`;
  meta.innerHTML = `<span>${escapeHtml(frame.relativePath)}</span><span>${state.selectedFrameIndex + 1} / ${state.selectedFrames.length}</span>
    <button type="button" id="prev-frame" class="icon-button" aria-label="Previous frame">‹</button>
    <button type="button" id="next-frame" class="icon-button" aria-label="Next frame">›</button>`;
  document.getElementById('prev-frame').addEventListener('click', () => {
    state.selectedFrameIndex = Math.max(0, state.selectedFrameIndex - 1);
    renderFrame();
  });
  document.getElementById('next-frame').addEventListener('click', () => {
    state.selectedFrameIndex = Math.min(state.selectedFrames.length - 1, state.selectedFrameIndex + 1);
    renderFrame();
  });
}

function replayFrames(files) {
  return files.filter(file => file.fileType === 'snapshot' || file.fileType === 'delta');
}

async function fetchJson(path) {
  const response = await fetch(path);
  if (!response.ok) {
    throw new Error(`Request failed: ${path}`);
  }
  return response.json();
}

function formatTime(value) {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function escapeAttribute(value) {
  return escapeHtml(value).replaceAll('`', '&#096;');
}
