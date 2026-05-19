import React from 'react';

const ITEMS = [
  { id: 'home',     label: 'Ставка',     section: 'OPERATIONS' },
  { id: 'news',     label: 'Сводка',     section: 'OPERATIONS' },
  { id: 'settings', label: 'Настройки',  section: 'SYSTEM' },
];

/**
 * Левая навигация. Стили — в launcher/src/styles/global.css (префикс .sidebar).
 */
export default function Sidebar({ currentPage, onNavigate }) {
  let lastSection = '';
  return (
    <aside className="sidebar">
      {ITEMS.map((item) => {
        const header = item.section !== lastSection ? (
          <div className="section-label" key={`sec-${item.section}`}>{item.section}</div>
        ) : null;
        lastSection = item.section;
        const active = currentPage === item.id;
        return (
          <React.Fragment key={item.id}>
            {header}
            <button
              type="button"
              className={`nav-item ${active ? 'active' : ''}`}
              onClick={() => onNavigate(item.id)}
            >
              {item.label}
            </button>
          </React.Fragment>
        );
      })}
      <div className="spacer" />
      <div className="footer-note">v3.0.0 · build 1.21.1</div>
    </aside>
  );
}
