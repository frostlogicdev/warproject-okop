// War Project — v2 site script.
// 1) Опрос /api/server/status и заполнение dashboard.
// 2) Scroll-reveal для элементов .reveal.
// 3) Подсветка активного пункта навигации.
// 4) Smooth scroll по якорям.

(function () {
  'use strict';

  var API = (window.WP_API_BASE) || 'http://localhost:4000';

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $all(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  function fmtUptime(seconds) {
    if (!seconds || seconds < 0) return '—';
    var d = Math.floor(seconds / 86400);
    var h = Math.floor((seconds % 86400) / 3600);
    var m = Math.floor((seconds % 3600) / 60);
    if (d > 0) return d + 'д ' + h + 'ч';
    if (h > 0) return h + 'ч ' + m + 'м';
    return m + 'м';
  }

  function setStatus(state, label) {
    var el = $('#srv-status');
    if (!el) return;
    var cls = state === 'online' ? 'ok' : (state === 'offline' ? 'bad' : 'warn');
    el.innerHTML = '<span class="status-dot ' + cls + '"></span>' + label;
  }

  function refreshStatus() {
    var hostEl = $('#srv-host');
    if (hostEl) hostEl.textContent = 'api: ' + API;

    fetch(API + '/api/server/status', { cache: 'no-store' })
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function (data) {
        var online = !!(data && (data.online === true || data.status === 'online'));
        setStatus(online ? 'online' : 'offline', online ? 'ONLINE' : 'OFFLINE');

        var players  = (data && (data.players  != null)) ? data.players  : '—';
        var maxSlots = (data && (data.max      != null)) ? data.max      : (data && data.slots) || '—';
        var tps      = (data && (data.tps      != null)) ? data.tps      : '—';
        var up       = (data && (data.uptime   != null)) ? data.uptime   : null;

        var pEl  = $('#srv-online'); if (pEl)  pEl.textContent  = String(players);
        var cEl  = $('#srv-cap');    if (cEl)  cEl.textContent  = 'из ' + maxSlots + ' слотов';
        var tEl  = $('#srv-tps');    if (tEl)  tEl.textContent  = (typeof tps === 'number') ? tps.toFixed(1) : String(tps);
        var uEl  = $('#srv-uptime'); if (uEl)  uEl.textContent  = 'uptime ' + fmtUptime(up);
      })
      .catch(function () {
        setStatus('offline', 'OFFLINE');
        var pEl = $('#srv-online'); if (pEl) pEl.textContent = '—';
        var cEl = $('#srv-cap');    if (cEl) cEl.textContent = 'нет связи с бэкендом';
        var tEl = $('#srv-tps');    if (tEl) tEl.textContent = '—';
        var uEl = $('#srv-uptime'); if (uEl) uEl.textContent = 'uptime —';
      });
  }

  function initReveal() {
    var items = $all('.reveal');
    if (!items.length || !('IntersectionObserver' in window)) {
      items.forEach(function (el) { el.classList.add('is-in'); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) {
          e.target.classList.add('is-in');
          io.unobserve(e.target);
        }
      });
    }, { rootMargin: '0px 0px -10% 0px', threshold: 0.12 });
    items.forEach(function (el) { io.observe(el); });
  }

  function initNavSpy() {
    var links = $all('.nav__link');
    var map = links.map(function (a) {
      var id = a.getAttribute('href');
      return id && id.charAt(0) === '#' ? { a: a, target: document.querySelector(id) } : null;
    }).filter(Boolean);
    if (!map.length || !('IntersectionObserver' in window)) return;
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) {
          map.forEach(function (m) { m.a.classList.toggle('is-active', m.target === e.target); });
        }
      });
    }, { rootMargin: '-40% 0px -55% 0px', threshold: 0 });
    map.forEach(function (m) { io.observe(m.target); });
  }

  function initSmoothScroll() {
    $all('a[href^="#"]').forEach(function (a) {
      a.addEventListener('click', function (ev) {
        var id = a.getAttribute('href');
        if (!id || id === '#') return;
        var t = document.querySelector(id);
        if (!t) return;
        ev.preventDefault();
        t.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    });
  }

  function boot() {
    initReveal();
    initNavSpy();
    initSmoothScroll();
    refreshStatus();
    setInterval(refreshStatus, 10000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
