// Landing client logic: smooth scroll + server status polling.
(function () {
  // Smooth anchors
  document.querySelectorAll('a[href^="#"]').forEach(a => {
    a.addEventListener('click', (e) => {
      const id = a.getAttribute('href').slice(1);
      const el = document.getElementById(id);
      if (!el) return;
      e.preventDefault();
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  });

  // Server status — endpoint matches launcher: GET /api/server/status
  const statusEl = document.getElementById('server-status');
  if (!statusEl) return;

  function setStatus(payload) {
    const online = payload && payload.online === true;
    statusEl.querySelector('[data-k="state"]').innerHTML =
      online
        ? '<span class="dot ok"></span>ONLINE'
        : '<span class="dot bad"></span>OFFLINE';
    statusEl.querySelector('[data-k="players"]').textContent =
      (payload && payload.players != null) ? String(payload.players) : '—';
    statusEl.querySelector('[data-k="version"]').textContent =
      (payload && payload.version) ? String(payload.version) : '1.21.1';
    statusEl.querySelector('[data-k="tps"]').textContent =
      (payload && payload.tps != null) ? Number(payload.tps).toFixed(1) : '—';
  }

  async function poll() {
    try {
      const res = await fetch('/api/server/status', { headers: { Accept: 'application/json' } });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      setStatus(await res.json());
    } catch (_) {
      setStatus({ online: false });
    }
  }

  poll();
  setInterval(poll, 30000);
})();
