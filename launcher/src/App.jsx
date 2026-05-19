import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import TitleBar from './components/TitleBar';
import Sidebar from './components/Sidebar';
import HomePage from './pages/HomePage';
import SettingsPage from './pages/SettingsPage';
import LoginOverlay from './components/LoginOverlay';

const API_BASE = 'http://localhost:4000/api';

export default function App() {
  const [currentPage, setCurrentPage] = useState('home');
  const [user, setUser] = useState(null);
  const [showLogin, setShowLogin] = useState(false);
  const [gameStatus, setGameStatus] = useState(null);

  useEffect(() => {
    // Check stored session
    const stored = localStorage.getItem('wp_session');
    if (stored) {
      try {
        const session = JSON.parse(stored);
        setUser(session);
      } catch (e) { localStorage.removeItem('wp_session'); }
    } else {
      setShowLogin(true);
    }

    // Listen for game status updates
    if (window.electronAPI?.onGameStatus) {
      const unsub = window.electronAPI.onGameStatus((data) => {
        setGameStatus(data);
        if (data.status === 'running') {
          setTimeout(() => setGameStatus(null), 3000);
        }
      });
      return unsub;
    }
  }, []);

  const handleLogin = async (username, password) => {
    try {
      const res = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.message || 'Ошибка авторизации');
      }
      const data = await res.json();
      const session = { username: data.username, token: data.token };
      localStorage.setItem('wp_session', JSON.stringify(session));
      setUser(session);
      setShowLogin(false);
      return { success: true };
    } catch (e) {
      return { success: false, error: e.message };
    }
  };

  const handleRegister = async (username, email, password) => {
    try {
      const res = await fetch(`${API_BASE}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, email, password })
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.message || 'Ошибка регистрации');
      }
      const data = await res.json();
      const session = { username: data.username, token: data.token };
      localStorage.setItem('wp_session', JSON.stringify(session));
      setUser(session);
      setShowLogin(false);
      return { success: true };
    } catch (e) {
      return { success: false, error: e.message };
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('wp_session');
    setUser(null);
    setShowLogin(true);
  };

  const handleLaunch = async () => {
    if (!user) {
      setShowLogin(true);
      return;
    }
    if (window.electronAPI?.launchGame) {
      await window.electronAPI.launchGame({ token: user.token, username: user.username });
    }
  };

  const pageVariants = {
    initial: { opacity: 0, y: 20 },
    animate: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.4, 0, 0.2, 1] } },
    exit: { opacity: 0, y: -20, transition: { duration: 0.2 } }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <TitleBar user={user} onLogout={handleLogout} />
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        <Sidebar currentPage={currentPage} onNavigate={setCurrentPage} />
        <main style={{ flex: 1, overflow: 'auto', padding: '32px', position: 'relative' }}>
          <AnimatePresence mode="wait">
            {currentPage === 'home' && (
              <motion.div key="home" {...pageVariants}>
                <HomePage
                  user={user}
                  gameStatus={gameStatus}
                  onLaunch={handleLaunch}
                  onLogin={() => setShowLogin(true)}
                />
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

      <AnimatePresence>
        {showLogin && (
          <LoginOverlay
            onLogin={handleLogin}
            onRegister={handleRegister}
            onClose={user ? () => setShowLogin(false) : undefined}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
