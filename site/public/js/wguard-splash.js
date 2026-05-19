// WGuard Splash — puzzle icon + animated ring + caption.
// Auto-hides after min display time, or on user input.
(function () {
  const MIN_MS = 2200;
  const MAX_MS = 4200;

  const root = document.createElement('div');
  root.id = 'wguard-splash';
  root.setAttribute('role', 'status');
  root.setAttribute('aria-live', 'polite');
  root.innerHTML = `
    <div class="wg-stack">
      <div class="wg-ring">
        <svg viewBox="0 0 168 168" aria-hidden="true">
          <circle class="track" cx="84" cy="84" r="78"></circle>
          <circle class="progress" cx="84" cy="84" r="78"></circle>
        </svg>
        <div class="wg-puzzle" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M20.5 11h-1.7c.1-.3.2-.6.2-1 0-1.4-1.1-2.5-2.5-2.5S14 8.6 14 10c0 .4.1.7.2 1H11V7.8c.3.1.6.2 1 .2 1.4 0 2.5-1.1 2.5-2.5S13.4 3 12 3 9.5 4.1 9.5 5.5c0 .4.1.7.2 1H6.5C5.7 6.5 5 7.2 5 8v3.5h1.7c-.1.3-.2.6-.2 1 0 1.4 1.1 2.5 2.5 2.5s2.5-1.1 2.5-2.5c0-.4-.1-.7-.2-1H14v3.2c-.3-.1-.6-.2-1-.2-1.4 0-2.5 1.1-2.5 2.5S11.6 19.5 13 19.5s2.5-1.1 2.5-2.5c0-.4-.1-.7-.2-1h3.7c.8 0 1.5-.7 1.5-1.5v-3z"/>
          </svg>
        </div>
        <div class="wg-scan"></div>
      </div>
      <div class="wg-caption">WGuard Protected</div>
      <div class="wg-sub">Integrity check • Signature verified</div>
    </div>
  `;
  document.documentElement.appendChild(root);

  const start = Date.now();
  let dismissed = false;

  function hide() {
    if (dismissed) return;
    dismissed = true;
    const elapsed = Date.now() - start;
    const wait = Math.max(0, MIN_MS - elapsed);
    setTimeout(() => {
      root.classList.add('hidden');
      setTimeout(() => root.remove(), 600);
    }, wait);
  }

  window.addEventListener('load', hide);
  setTimeout(hide, MAX_MS);
  ['click','keydown','pointerdown'].forEach(ev =>
    window.addEventListener(ev, hide, { once: true, passive: true }));
})();
