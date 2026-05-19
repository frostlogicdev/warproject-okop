import React, { useState } from 'react';

/**
 * Overlay авторизации / регистрации.
 * onLogin(username, password) и onRegister(username, email, password) — async,
 * возвращают { success, error? }.
 * Анимация — через CSS-класс .animate-fade-in (см. global.css).
 */
export default function LoginOverlay({ onLogin, onRegister, onClose }) {
  const [mode, setMode] = useState('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setErr('');
    if (!username.trim() || !password) { setErr('Заполните все поля.'); return; }
    if (mode === 'register' && !email.trim()) { setErr('Укажите email.'); return; }
    setBusy(true);
    try {
      const result = mode === 'login'
        ? await onLogin(username.trim(), password)
        : await onRegister(username.trim(), email.trim(), password);
      if (!result || result.success !== true) {
        setErr((result && result.error) || 'Операция не выполнена.');
      }
    } finally { setBusy(false); }
  };

  return (
    <div className="login-overlay animate-fade-in" role="dialog" aria-modal="true">
      <div className="panel login-card animate-fade-in">
        <h2>Доступ к операции</h2>
        <p className="sub">Авторизация через War Project ID. WGuard верифицирует клиент.</p>

        <div className="login-tabs">
          <div className={`tab ${mode === 'login' ? 'active' : ''}`} onClick={() => setMode('login')}>Вход</div>
          <div className={`tab ${mode === 'register' ? 'active' : ''}`} onClick={() => setMode('register')}>Регистрация</div>
        </div>

        <form className="login-form" onSubmit={submit}>
          <input className="input" type="text" placeholder="Позывной (username)"
                 value={username} onChange={(e) => setUsername(e.target.value)}
                 autoFocus autoComplete="username" />
          {mode === 'register' && (
            <input className="input" type="email" placeholder="Email"
                   value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
          )}
          <input className="input" type="password" placeholder="Пароль"
                 value={password} onChange={(e) => setPassword(e.target.value)}
                 autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
          {err && <div className="login-err">{err}</div>}
          <button className="btn" type="submit" disabled={busy}>
            {busy ? 'ждём…' : (mode === 'login' ? 'Войти' : 'Создать')}
          </button>
          {onClose && (
            <button className="btn ghost" type="button" onClick={onClose} disabled={busy}>Отмена</button>
          )}
        </form>
      </div>
    </div>
  );
}
