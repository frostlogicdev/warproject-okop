import React from 'react';
import { motion } from 'framer-motion';

const navItems = [
  { id: 'home', label: 'Главная', icon: '⚔' },
  { id: 'settings', label: 'Настройки', icon: '⚙' }
];

export default function Sidebar({ currentPage, onNavigate }) {
  return (
    <nav style={{
      width: 220,
      background: 'var(--bg-secondary)',
      borderRight: '1px solid var(--border)',
      padding: '24px 12px',
      display: 'flex',
      flexDirection: 'column',
      gap: 4
    }}>
      <div style={{ marginBottom: 24, padding: '0 12px' }}>
        <h2 style={{
          fontFamily: "'Orbitron', sans-serif",
          fontSize: 18,
          fontWeight: 800,
          background: 'var(--gradient-red)',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent',
          letterSpacing: '0.02em'
        }}>
          WAR PROJECT
        </h2>
        <p style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
          Военный режим Minecraft
        </p>
      </div>

      {navItems.map(item => (
        <NavItem
          key={item.id}
          item={item}
          active={currentPage === item.id}
          onClick={() => onNavigate(item.id)}
        />
      ))}

      <div style={{ flex: 1 }} />

      <div style={{
        padding: '12px',
        borderRadius: 'var(--radius-sm)',
        background: 'rgba(255, 51, 51, 0.05)',
        border: '1px solid rgba(255, 51, 51, 0.1)'
      }}>
        <p style={{ fontSize: 11, color: 'var(--text-muted)', lineHeight: 1.5 }}>
          Сервер: <span style={{ color: 'var(--success)' }}>Online</span>
        </p>
        <p style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
          Игроков: <span style={{ color: 'var(--text-secondary)' }}>—</span>
        </p>
      </div>
    </nav>
  );
}

function NavItem({ item, active, onClick }) {
  const [hovered, setHovered] = React.useState(false);

  return (
    <motion.button
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      whileTap={{ scale: 0.97 }}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '10px 12px',
        borderRadius: 'var(--radius-sm)',
        background: active ? 'rgba(255, 51, 51, 0.1)' : hovered ? 'rgba(255, 255, 255, 0.03)' : 'transparent',
        border: active ? '1px solid rgba(255, 51, 51, 0.2)' : '1px solid transparent',
        color: active ? 'var(--accent)' : 'var(--text-secondary)',
        fontSize: 13,
        fontWeight: active ? 600 : 400,
        width: '100%',
        textAlign: 'left',
        transition: 'var(--transition)',
        position: 'relative'
      }}
    >
      {active && (
        <motion.div
          layoutId="activeIndicator"
          style={{
            position: 'absolute',
            left: 0,
            top: '50%',
            transform: 'translateY(-50%)',
            width: 3,
            height: 20,
            borderRadius: 2,
            background: 'var(--accent)'
          }}
        />
      )}
      <span style={{ fontSize: 16 }}>{item.icon}</span>
      <span>{item.label}</span>
    </motion.button>
  );
}
