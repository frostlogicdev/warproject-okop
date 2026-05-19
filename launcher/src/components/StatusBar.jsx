import React from 'react';

export default function StatusBar({ serverStatus }) {
  const online = serverStatus && serverStatus.online === true;
  return (
    <div style=
      height: 28,
      borderTop: '1px solid var(--line)',
      background: 'var(--bg-1)',
      display: 'flex',
      alignItems: 'center',
      gap: 16,
      padding: '0 14px',
      fontFamily: 'Share Tech Mono, monospace',
      fontSize: 11,
      letterSpacing: '.14em',
      color: 'var(--muted)',
      textTransform: 'uppercase'
    >
      <span><span className={`dot ${online ? 'ok' : 'bad'}`} style= marginRight: 6  />
        {online ? 'server online' : 'server offline'}
      </span>
      <span>players: {serverStatus && serverStatus.players != null ? serverStatus.players : '—'}</span>
      <span>tps: {serverStatus && serverStatus.tps != null ? Number(serverStatus.tps).toFixed(1) : '—'}</span>
      <span style= marginLeft: 'auto' >WGuard • protected</span>
    </div>
  );
}
