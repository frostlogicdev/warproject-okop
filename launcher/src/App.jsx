import React, { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import TitleBar from './components/TitleBar';
import Sidebar from './components/Sidebar';
import HomePage from './pages/HomePage';
import NewsPage from './pages/NewsPage';
import SettingsPage from './pages/SettingsPage';
import LoginOverlay from './components/LoginOverlay';
import StatusBar from './components/StatusBar';
import WGuardSplash from './components/WGuardSplash';

const API_BASE = 'http://localhost:4000/api';

export default function App() {
  const [splashDone, setSplashDone] = useState(false);
  const [currentPage, setCurrentPage] = useState('home');
  const [user, setUser] = useState(null);
  const [showLogin, setShowLogin] = useState(false);
  const [gameStatus, setGameStatus] = useState(null);
  const [serverStatus, setServerStatus] = useState(null);

  // Session bootstrap
  useEffect(() => {
    const stored = localStorage.getItem('wp_session');
    if (stored) {
      try { setUser(JSON.parse(stored)); }
      catch { localStorage.removeItem('wp_session'); }
    }
  }, []);

  // Show login overlay only after splash, if no session
  useEffect(() => {
    if (splashDone && !user) setShowLogin(true);
  }, [splashDone, user]);

  // Game status listener
  useEffect(() => {
    if (!window.electronAPI?.onGameStatus) return;
    const unsub = window.electronAPI.onGameStatus((data) => {
      setGameStatus(data);
      if (data.status === 'running' || data.status === 'stopped') {
        setTimeout(() => setGameStatus(null), 3500);
      }
    });
    return unsub;
  }, []);

  // Server status polling
  useEffect(() => {
    let stopped = false;
    const poll = async () => {
      try {
        const res = await fetch(`${API_BASE}/server/status`, { headers: { Accept: 'application/json' } });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const json = await res.json();
        if (!stopped) setServerStatus(json);
      } catch {
        if (!stopped) setServerStatus({ online: false });
      }
    };
    poll();
    const id = setInterval(poll, 30000);
    return () => { stopped = true; clearInterval(id); };
  }, []);

  const handleLogin = useCallback(async (username, password) => {
    try {
      const res = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.message || 'Ошибка авторизации');
      const session = { username: data.username, token: data.token };
      localStorage.setItem('wp_session', JSON.stringify(session));
      setUser(session);
      setShowLogin(false);
      return { success: true };
    } catch (e) {
      return { success: false, error: e.message };
    }
  }, []);

  const handleRegister = useCallback(async (username, email, password) => {
    try {
      const res = await fetch(`${API_BASE}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, email, password })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.message || 'Ошибка регистрации');
      const session = { username: data.username, token: data.token };
      localStorage.setItem('wp_session', JSON.stringify(session));
      setUser(session);
      setShowLogin(false);
      return { success: true };
    } catch (e) {
      return { success: false, error: e.message };
    }
  }, []);

  const handleLogout = useCallback(() => {
    localStorage.removeItem('wp_session');
    setUser(null);
    setShowLogin(true);
  }, []);

  const handleLaunch = useCallback(async () => {
    if (!user) { setShowLogin(true); return; }
    if (window.electronAPI?.launchGame) {
      await window.electronAPI.launchGame({ token: user.token, username: user.username });
    }
  }, [user]);

  const pageVariants = {
    initial: { opacity: 0, y: 12 },
    animate: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.4, 0, 0.2, 1] } },
    exit:    { opacity: 0, y: -10, transition: { duration: 0.18 } }
  };

  return (
    <>
      {!splashDone && <WGuardSplash onDone={() => setSplashDone(true)} />}

      <div className="app-shell">
        <TitleBar user={user} onLogout={handleLogout} />
        <div className="app-body">
          <Sidebar currentPage={currentPage} onNavigate={setCurrentPage} />
          <main className="app-main">
            <AnimatePresence mode="wait">
              {currentPage === 'home' && (
                <motion.div key="home" {...pageVariants}>
                  <HomePage
                    user={user}
                    serverStatus={serverStatus}
                    onLaunch={handleLaunch}
                    onLogin={() => setShowLogin(true)}
                  />
                </motion.div>
              )}
              {currentPage === 'news' && (
                <motion.div key="news" {...pageVariants}>
                  <NewsPage />
                </motion.div>
              )}
              {currentPage === 'settings' && (
                <motion.div key="settings" {...pageVariants}>
                  <SettingsPage />
                </motion.div>
              )}
            </AnimatePresence>
          </main>
        </div>
        <StatusBar serverStatus={serverStatus} />
      </div>

      {gameStatus && (
        <div className="game-toast" role="status">
          <span className={`dot ${gameStatus.status === 'running' ? 'ok' : gameStatus.status === 'error' ? 'bad' : 'warn'}`} />
          {gameStatus.message || gameStatus.status}
        </div>
      )}

      <AnimatePresence>
        {showLogin && splashDone && (
          <LoginOverlay
            onLogin={handleLogin}
            onRegister={handleRegister}
            onClose={user ? () => setShowLogin(false) : undefined}
          />
        )}
      </AnimatePresence>
    </>
  );
}
