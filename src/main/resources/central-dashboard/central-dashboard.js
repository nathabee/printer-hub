fetch('/api/central/farms/overview')
  .then(response => response.json())
  .then(data => {
    const rows = data.farms.map(item => {
      const farm = item.farm;
      return `<tr><td>${escapeHtml(farm.farmName)}</td><td class="status">${escapeHtml(item.status)}</td><td>${farm.printerCount}</td><td>${farm.cameraCount}</td><td>${farm.activePrintCount}</td><td>${farm.spaghettiAlertCount}</td><td>${escapeHtml(farm.lastSeenAt || '')}</td></tr>`;
    }).join('') || '<tr><td colspan="7">No farms registered.</td></tr>';
    document.getElementById('farms').innerHTML = rows;
    return loadReplays(data.farms.map(item => item.farm));
  });

async function loadReplays(farms) {
  const replays = [];
  for (const farm of farms) {
    const response = await fetch(`/api/central/farms/${encodeURIComponent(farm.farmId)}/camera-replay-packages`);
    const data = await response.json();
    for (const replay of data.packages || []) {
      replays.push({ farm, replay });
    }
  }

  if (replays.length === 0) {
    document.getElementById('replays').innerHTML = '<p class="empty">No replay packages uploaded.</p>';
    return;
  }

  const items = await Promise.all(replays.map(async ({ farm, replay }) => {
    const detailResponse = await fetch(`/api/central/camera-replay-packages/${encodeURIComponent(replay.packageId)}`);
    const detail = await detailResponse.json();
    const files = (detail.files || []).filter(file => file.fileType === 'snapshot' || file.fileType === 'delta');
    const links = files.slice(0, 8).map(file => `<a href="${escapeAttribute(file.url)}" target="_blank" rel="noopener">${escapeHtml(file.relativePath)}</a>`).join('');
    return `<article class="replay-item">
      <h3>${escapeHtml(replay.label || replay.cameraJobId || replay.packageId)}</h3>
      <p>${escapeHtml(farm.farmName)} · ${escapeHtml(replay.cameraId || '')} · ${replay.frameCount} frames</p>
      <div class="replay-links">${links || '<span>No replayable frames.</span>'}</div>
    </article>`;
  }));

  document.getElementById('replays').innerHTML = items.join('');
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function escapeAttribute(value) {
  return escapeHtml(value).replaceAll('`', '&#096;');
}
