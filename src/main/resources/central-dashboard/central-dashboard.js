fetch('/api/central/farms/overview')
  .then(response => response.json())
  .then(data => {
    const rows = data.farms.map(item => {
      const farm = item.farm;
      return `<tr><td>${escapeHtml(farm.farmName)}</td><td class="status">${escapeHtml(item.status)}</td><td>${farm.printerCount}</td><td>${farm.cameraCount}</td><td>${farm.activePrintCount}</td><td>${farm.spaghettiAlertCount}</td><td>${escapeHtml(farm.lastSeenAt || '')}</td></tr>`;
    }).join('') || '<tr><td colspan="7">No farms registered.</td></tr>';
    document.getElementById('farms').innerHTML = rows;
  });

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}
