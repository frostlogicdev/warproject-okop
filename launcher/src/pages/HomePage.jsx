import React from 'react';
import { motion } from 'framer-motion';

export default function HomePage({ user, gameStatus, onLaunch, onLogin }) {
  return (
    <div style={{ maxWidth: 900, margin: '0 auto' }}>
      {/* Hero Section */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        style={{
          background: 'linear-gradient(135deg, rgba(255, 51, 51, 0.08) 0%, rgba(20, 20, 30, 0.8) 100%)',
          borderRadius: 'var(--radius)',
          border: '1px solid rgba(255, 51, 51, 0.15)',
          padding: '48px 40px',
          marginBottom: 32,
          position: 'relative',
          overflow: 'hidden'
        }}
      >
        {/* Background decoration */}
        <div style={{
          position: 'absolute',
          top: -50, right: -50,
          width: 200, height: 200,
          background: 'radial-gradient(circle, rgba(255, 51, 51, 0.1) 0%, transparent 70%)',
          borderRadius: '50%'
        }} />

        <h1 style={{
          fontFamily: "'Orbitron', sans-serif",
          fontSize: 36,
          fontWeight: 900,
          marginBottom: 12,
          background: 'linear-gradient(135deg, #fff 0%, #ccc 100%)',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent'
        }}>
          WAR PROJECT
        </h1>
        <p style={{
          fontSize: 16,
          color: 'var(--text-secondary)',
          maxWidth: 500,
          lineHeight: 1.6,
          marginBottom: 32
        }}>
          Военный режим Minecraft с реалистичными механиками: фракции, звания, 
          дипломатия, окопы и укрепления. Выбери сторону и вступи в бой.
        </p>

        {/* Launch Button */}
        <LaunchButton
          user={user}
          gameStatus={gameStatus}
          onLaunch={onLaunch}
          onLogin={onLogin}
        />
      </motion.div>

      {/* Features Grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: 16
      }}>
        <FeatureCard
          icon="⚔"
          title="Две фракции"
          description="Зарнавия и Черноград — выбери сторону конфликта"
          delay={0.2}
        />
        <FeatureCard
          icon="🎖"
          title="Система званий"
          description="От рядового до генерала — продвигайся по службе"
          delay={0.3}
        />
        <FeatureCard
          icon="🏰"
          title="Укрепления"
          description="31 уникальный блок для строительства окопов и баз"
          delay={0.4}
        />
        <FeatureCard
          icon="📋"
          title="Документы"
          description="Паспорта, военные билеты и система пленения"
          delay={0.5}
        />
        <FeatureCard
          icon="🤝"
          title="Дипломатия"
          description="Перемирия, обмены пленными и переговоры"
          delay={0.6}
        />
        <FeatureCard
          icon="🗺"
          title="Карта"
          description="Интеграция с JourneyMap — маркеры и территории"
          delay={0.7}
        />
      </div>
    </div>
  );
}

function LaunchButton({ user, gameStatus, onLaunch, onLogin }) {
  const isLaunching = gameStatus && gameStatus.status !== 'running';
  const isRunning = gameStatus?.status === 'running';

  if (!user) {
    return (
      <motion.button
        whileHover={{ scale: 1.03 }}
        whileTap={{ scale: 0.97 }}
        onClick={onLogin}
        style={{
          padding: '16px 48px',
          background: 'var(--gradient-red)',
          color: '#fff',
          fontSize: 16,
          fontWeight: 700,
          borderRadius: 'var(--radius-sm)',
          boxShadow: 'var(--shadow-glow)',
          fontFamily: "'Orbitron', sans-serif",
          letterSpacing: '0.05em'
        }}
      >
        ВОЙТИ
      </motion.button>
    );
  }

  return (
    <div>
      <motion.button
        whileHover={!isLaunching ? { scale: 1.03 } : {}}
        whileTap={!isLaunching ? { scale: 0.97 } : {}}
        onClick={!isLaunching ? onLaunch : undefined}
        disabled={isLaunching}
        style={{
          padding: '16px 48px',
          background: isRunning ? 'linear-gradient(135deg, #33ff66, #00cc44)' :
                     isLaunching ? 'var(--border)' : 'var(--gradient-red)',
          color: '#fff',
          fontSize: 16,
          fontWeight: 700,
          borderRadius: 'var(--radius-sm)',
          boxShadow: isRunning ? '0 0 20px rgba(51, 255, 102, 0.3)' :
                     isLaunching ? 'none' : 'var(--shadow-glow)',
          fontFamily: "'Orbitron', sans-serif",
          letterSpacing: '0.05em',
          transition: 'var(--transition)',
          cursor: isLaunching ? 'not-allowed' : 'pointer'
        }}
      >
        {isRunning ? '✓ ЗАПУЩЕНО' : isLaunching ? 'ЗАПУСК...' : 'ИГРАТЬ'}
      </motion.button>

      {/* Progress bar */}
      {isLaunching && gameStatus && (
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          style={{ marginTop: 16, maxWidth: 300 }}
        >
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            marginBottom: 6
          }}>
            <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
              {gameStatus.label || 'Подготовка...'}
            </span>
            <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              {gameStatus.progress}%
            </span>
          </div>
          <div style={{
            height: 4,
            background: 'var(--border)',
            borderRadius: 2,
            overflow: 'hidden'
          }}>
            <motion.div
              initial={{ width: 0 }}
              animate={{ width: `${gameStatus.progress}%` }}
              transition={{ duration: 0.5, ease: 'easeOut' }}
              style={{
                height: '100%',
                background: 'var(--gradient-red)',
                borderRadius: 2
              }}
            />
          </div>
        </motion.div>
      )}
    </div>
  );
}

function FeatureCard({ icon, title, description, delay }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay }}
      whileHover={{ y: -4, borderColor: 'rgba(255, 51, 51, 0.3)' }}
      style={{
        padding: '24px',
        background: 'var(--bg-card)',
        borderRadius: 'var(--radius)',
        border: '1px solid var(--border)',
        transition: 'var(--transition)'
      }}
    >
      <div style={{
        fontSize: 28,
        marginBottom: 12,
        filter: 'drop-shadow(0 2px 4px rgba(0,0,0,0.3))'
      }}>
        {icon}
      </div>
      <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 6 }}>{title}</h3>
      <p style={{ fontSize: 12, color: 'var(--text-muted)', lineHeight: 1.5 }}>{description}</p>
    </motion.div>
  );
}
