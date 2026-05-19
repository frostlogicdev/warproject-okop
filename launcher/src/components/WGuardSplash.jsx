import React, { useEffect, useState } from 'react';

/**
 * WGuard Splash — показывается при запуске лаунчера.
 * Анимированный пазл-значок, обводка и подпись WGuard Protected.
 * Скрывается через onDone (по таймеру или по клику/клавиатуре).
 */
export default function WGuardSplash({ minMs = 2200, maxMs = 4200, onDone }) {
  const [hiding, setHiding] = useState(false);
  const [removed, setRemoved] = useState(false);

  useEffect(() => {
    const start = Date.now();
    let done = false;
    const finish = () => {
      if (done) return;
      done = true;
      const wait = Math.max(0, minMs - (Date.now() - start));
      setTimeout(() => {
        setHiding(true);
        setTimeout(() => { setRemoved(true); onDone && onDone(); }, 520);
      }, wait);
    };
    const t = setTimeout(finish, maxMs);
    const onAny = () => finish();
    window.addEventListener('click', onAny, { once: true });
    window.addEventListener('keydown', onAny, { once: true });
    return () => {
      clearTimeout(t);
      window.removeEventListener('click', onAny);
      window.removeEventListener('keydown', onAny);
    };
  }, [minMs, maxMs, onDone]);

  if (removed) return null;

  return (
    <div className={`wg-splash ${hiding ? 'wg-splash--hide' : ''}`} role="status" aria-live="polite">
      <div className="wg-stack">
        <div className="wg-ring">
          <svg viewBox="0 0 168 168" aria-hidden="true">
            <circle className="track" cx="84" cy="84" r="78" />
            <circle className="progress" cx="84" cy="84" r="78" />
          </svg>
          <div className="wg-puzzle" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M20.5 11h-1.7c.1-.3.2-.6.2-1 0-1.4-1.1-2.5-2.5-2.5S14 8.6 14 10c0 .4.1.7.2 1H11V7.8c.3.1.6.2 1 .2 1.4 0 2.5-1.1 2.5-2.5S13.4 3 12 3 9.5 4.1 9.5 5.5c0 .4.1.7.2 1H6.5C5.7 6.5 5 7.2 5 8v3.5h1.7c-.1.3-.2.6-.2 1 0 1.4 1.1 2.5 2.5 2.5s2.5-1.1 2.5-2.5c0-.4-.1-.7-.2-1H14v3.2c-.3-.1-.6-.2-1-.2-1.4 0-2.5 1.1-2.5 2.5S11.6 19.5 13 19.5s2.5-1.1 2.5-2.5c0-.4-.1-.7-.2-1h3.7c.8 0 1.5-.7 1.5-1.5v-3z"/>
            </svg>
          </div>
          <div className="wg-scan" />
        </div>
        <div className="wg-caption">WGuard Protected</div>
        <div className="wg-sub">Integrity check • Signature verified</div>
      </div>
    </div>
  );
}
