import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export default function SettingsPage() {
  const [settings, setSettings] = useState({
    ram: 4,
    javaPath: 'java',
    gameDir: '',
    fullscreen: false,
    width: 1920,
    height: 1080
  });
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (window.electronAPI?.loadSettings) {
      window.electronAPI.loadSettings().then(s => setSettings(s));
    }
  }, []);

  const handleSave = async () => {
    if (window.electronAPI?.saveSettings) {
      await window.electronAPI.saveSettings(settings);
    }
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const update = (key, value) => {
    setSettings(prev => ({ ...prev, [key]: value }));
    setSaved(false);
  };

  return (
    <div style={{ maxWidth: 700, margin: '0 auto' }}>
      <motion.h1
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        style={{
          fontFamily: "'Orbitron', sans-serif",
          fontSize: 24,
          fontWeight: 700,
          marginBottom: 32
        }}
      >
        Настройки
      </motion.h1>

      {/* Memory */}
      <SettingsSection title="Память" delay={0.1}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <input
            type="range"
            min={2}
            max={16}
            step={1}
            value={settings.ram}
            onChange={e => update('ram', parseInt(e.target.value))}
            style={{
              flex: 1,
              height: 4,
              appearance: 'none',
              background: `linear-gradient(to right, var(--accent) 0%, var(--accent) ${(settings.ram - 2) / 14 * 100}%, var(--border) ${(settings.ram - 2) / 14 * 100}%, var(--border) 100%)`,
              borderRadius: 2,
              cursor: 'pointer'
            }}
          />
          <span style={{
            minWidth: 60,
            textAlign: 'center',
            padding: '6px 12px',
            background: 'var(--bg-primary)',
            borderRadius: 'var(--radius-sm)',
            border: '1px solid var(--border)',
            fontSize: 14,
            fontWeight: 600
          }}>
            {settings.ram} GB
          </span>
        </div>
        <p style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 8 }}>
          Рекомендуется 4-8 GB для комфортной игры с модами
        </p>
      </SettingsSection>

      {/* Java */}
      <SettingsSection title="Java" delay={0.2}>
        <InputField
          label="Путь к Java"
          value={settings.javaPath}
          onChange={v => update('javaPath', v)}
          placeholder="java"
        />
      </SettingsSection>

      {/* Game Directory */}
      <SettingsSection title="Директория игры" delay={0.3}>
        <InputField
          label="Папка с файлами игры"
          value={settings.gameDir}
          onChange={v => update('gameDir', v)}
          placeholder="C:\Users\...\.warproject"
        />
      </SettingsSection>

      {/* Display */}
      <SettingsSection title="Экран" delay={0.4}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
          <ToggleSwitch
            checked={settings.fullscreen}
            onChange={v => update('fullscreen', v)}
          />
          <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Полноэкранный режим</span>
        </div>

        {!settings.fullscreen && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            style={{ display: 'flex', gap: 12 }}
          >
            <InputField
              label="Ширина"
              value={settings.width}
              onChange={v => update('width', parseInt(v) || 0)}
              type="number"
              style={{ flex: 1 }}
            />
            <InputField
              label="Высота"
              value={settings.height}
              onChange={v => update('height', parseInt(v) || 0)}
              type="number"
              style={{ flex: 1 }}
            />
          </motion.div>
        )}
      </SettingsSection>

      {/* Save Button */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.5 }}
        style={{ marginTop: 32, display: 'flex', gap: 12, alignItems: 'center' }}
      >
        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={handleSave}
          style={{
            padding: '12px 32px',
            background: saved ? 'linear-gradient(135deg, #33ff66, #00cc44)' : 'var(--gradient-red)',
            color: '#fff',
            fontSize: 14,
            fontWeight: 600,
            borderRadius: 'var(--radius-sm)',
            boxShadow: saved ? '0 0 15px rgba(51, 255, 102, 0.3)' : 'var(--shadow-glow)',
            transition: 'var(--transition)'
          }}
        >
          {saved ? '✓ Сохранено' : 'Сохранить'}
        </motion.button>
      </motion.div>
    </div>
  );
}

function SettingsSection({ title, children, delay }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 15 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay }}
      style={{
        padding: '24px',
        background: 'var(--bg-card)',
        borderRadius: 'var(--radius)',
        border: '1px solid var(--border)',
        marginBottom: 16
      }}
    >
      <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16, color: 'var(--text-secondary)' }}>
        {title}
      </h3>
      {children}
    </motion.div>
  );
}

function InputField({ label, value, onChange, placeholder, type = 'text', style = {} }) {
  return (
    <div style={style}>
      {label && (
        <label style={{ display: 'block', fontSize: 11, color: 'var(--text-muted)', marginBottom: 6 }}>
          {label}
        </label>
      )}
      <input
        type={type}
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        style={{
          width: '100%',
          padding: '10px 14px',
          background: 'var(--bg-primary)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-sm)',
          color: 'var(--text-primary)',
          fontSize: 13,
          transition: 'var(--transition)'
        }}
        onFocus={e => e.target.style.borderColor = 'var(--accent)'}
        onBlur={e => e.target.style.borderColor = 'var(--border)'}
      />
    </div>
  );
}

function ToggleSwitch({ checked, onChange }) {
  return (
    <motion.button
      onClick={() => onChange(!checked)}
      style={{
        width: 44,
        height: 24,
        borderRadius: 12,
        background: checked ? 'var(--accent)' : 'var(--border)',
        padding: 3,
        display: 'flex',
        alignItems: 'center',
        justifyContent: checked ? 'flex-end' : 'flex-start',
        transition: 'var(--transition)'
      }}
    >
      <motion.div
        layout
        style={{
          width: 18,
          height: 18,
          borderRadius: '50%',
          background: '#fff'
        }}
      />
    </motion.button>
  );
}
