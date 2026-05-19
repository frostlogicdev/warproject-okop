import React from 'react';

/**
 * Title bar (drag-region).
 * Стили — в launcher/src/styles/global.css.
 */
export default function TitleBar({ user, onLogout }) {
  const win = (action) => {
    if (window.electronAPI && window.electronAPI.window) window.electronAPI.window(action);
  };
  return (
    <header className="titlebar">
      <div className="brand">
        <span className="brand-mark"><span>W</span></span>
        War Project
      </div>
      <div className="titlebar-user">
        {user ? (
          <>
            <span>OPERATOR: {user.username}</span>
            <button className="btn ghost btn-xs" onClick={onLogout}>EXIT</button>
          </>
        ) : (
          <span>OPERATOR: —</span>
        )}
      </div>
      <div className="winbtns">
        <button className="winbtn" title="Свернуть" onClick={() => win('minimize')}>–</button>
        <button className="winbtn" title="Максимизировать" onClick={() => win('maximize')}>▢</button>
        <button className="winbtn close" title="Закрыть" onClick={() => win('close')}>×</button>
      </div>
    </header>
  );
}
