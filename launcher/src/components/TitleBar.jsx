import React from 'react';
import { motion } from 'framer-motion';

export default function TitleBar({ user, onLogout }) {
  return (
    <div style={{
      height: 40,
      background: 'var(--bg-secondary)',
      borderBottom: '1px solid var(--border)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 16px',
      WebkitAppRegion: 'drag',
      userSelect: 'none'
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{
          width: 20, height: 20,
          background: 'var(--gradient-red)',
          borderRadius: 4,
          display: 'flex', alignItems: 'center', justifyContent: 'center'
        }}>
          <span style={{ fontSize: 10, fontWeight: 900, color: '#fff' }}>W</span>
        </div>
        <span style={{
          fontFamily: "'Orbitron', sans-serif",
          fontSize: 12,
          fontWeight: 600,
          letterSpacing: '0.05em',
          color: 'var(--text-secondary)'
        }}>
          WAR PROJECT
        </span>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12, WebkitAppRegion: 'no-drag' }}>
        {user && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            style={{ display: 'flex', alignItems: 'center', gap: 8 }}
          >
            <div style={{
              width: 8, height: 8,
              borderRadius: '50%',
              background: 'var(--success)',
              boxShadow: '0 0 6px rgba(51, 255, 102, 0.5)'
            }} />
            <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{user.username}</span>
            <button
              onClick={onLogout}
              style={{
                background: 'none',
                color: 'var(--text-muted)',
                fontSize: 11,
                padding: '2px 8px',
                borderRadius: 4,
                transition: 'var(--transition)'
              }}
              onMouseEnter={e => e.target.style.color = 'var(--accent)'}
              onMouseLeave={e => e.target.style.color = 'var(--text-muted)'}
            >
              Выйти
            </button>
          </motion.div>
        )}

        <div style={{ display: 'flex', gap: 4 }}>
          <WindowButton icon="─" onClick={() => window.electronAPI?.minimize()} />
          <WindowButton icon="□" onClick={() => window.electronAPI?.maximize()} />
          <WindowButton icon="✕" onClick={() => window.electronAPI?.close()} isClose />
        </div>
      </div>
    </div>
  );
}

function WindowButton({ icon, onClick, isClose }) {
  const [hovered, setHovered] = React.useState(false);
  return (
    <button
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        width: 32, height: 28,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        background: hovered ? (isClose ? '#e81123' : 'rgba(255,255,255,0.08)') : 'transparent',
        color: hovered && isClose ? '#fff' : 'var(--text-secondary)',
        fontSize: 12,
        borderRadius: 4,
        transition: 'var(--transition)'
      }}
    >
      {icon}
    </button>
  );
}
