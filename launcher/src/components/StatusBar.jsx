import React from 'react';

/**
 * Нижняя строка статуса: состояние сервера, игроки, TPS и метка WGuard.
 * Стили — в launcher/src/styles/global.css.
 */
export default function StatusBar({ serverStatus }) {
  const online = serverStatus && serverStatus.online === true;
  return (
    <footer className="app-statusbar">
      <span className="app-statusbar__item">
        <span className={`dot ${online ? 'ok' : 'bad'}`} />
        <span>{online ? 'server online' : 'server offline'}</span>
      </span>
      <span className="app-statusbar__item">
        players: {serverStatus && serverStatus.players != null ? serverStatus.players : '—'}
      </span>
      <span className="app-statusbar__item">
        tps: {serverStatus && serverStatus.tps != null ? Number(serverStatus.tps).toFixed(1) : '—'}
      </span>
      <span className="app-statusbar__item app-statusbar__wguard">WGuard • protected</span>
    </footer>
  );
}
