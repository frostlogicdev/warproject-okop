// WGuard Splash dismisser.
// HTML уже рендерит <div id="wg-splash" class="wg-splash">. Задача скрипта —
// гарантированно добавить класс .wg-splash--hide (CSS разбирается с fade-out + display:none).
(function () {
  'use strict';
  var MIN_MS = 1800;
  var MAX_MS = 3000;

  function hide(splash) {
    if (!splash || splash.dataset.wgHidden === '1') return;
    splash.dataset.wgHidden = '1';
    splash.classList.add('wg-splash--hide');
    // Резервная гарантия: жёстко убираем из потока через секунду.
    setTimeout(function () {
      if (splash && splash.parentNode) splash.parentNode.removeChild(splash);
    }, 1000);
  }

  function init() {
    var splash = document.getElementById('wg-splash');
    if (!splash) return;
    var start = Date.now();
    var scheduled = false;
    function schedule() {
      if (scheduled) return; scheduled = true;
      var wait = Math.max(0, MIN_MS - (Date.now() - start));
      setTimeout(function () { hide(splash); }, wait);
    }
    // Основные триггеры: window.load или MAX_MS.
    if (document.readyState === 'complete') {
      schedule();
    } else {
      window.addEventListener('load', schedule, { once: true });
    }
    setTimeout(schedule, MAX_MS);
    // Дисмисс на первом вводе.
    ['click', 'keydown', 'pointerdown'].forEach(function (ev) {
      window.addEventListener(ev, schedule, { once: true, passive: true });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
