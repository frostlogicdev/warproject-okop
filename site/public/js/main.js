// ═══════════════════════════════════════════════════════════════════════
// War Project — Site JavaScript
// ═══════════════════════════════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', () => {
  initScrollAnimations();
  initNavbar();
  initParticles();
  fetchServerStatus();
});

// ─── Scroll Animations ────────────────────────────────────────────────────────

function initScrollAnimations() {
  const elements = document.querySelectorAll('.animate-on-scroll');

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const delay = entry.target.dataset.delay || 0;
        setTimeout(() => {
          entry.target.classList.add('visible');
        }, parseInt(delay));
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.1, rootMargin: '0px 0px -50px 0px' });

  elements.forEach(el => observer.observe(el));
}

// ─── Navbar Scroll Effect ─────────────────────────────────────────────────────

function initNavbar() {
  const navbar = document.getElementById('navbar');
  let lastScroll = 0;

  window.addEventListener('scroll', () => {
    const currentScroll = window.scrollY;
    if (currentScroll > 50) {
      navbar.classList.add('scrolled');
    } else {
      navbar.classList.remove('scrolled');
    }
    lastScroll = currentScroll;
  });
}

// ─── Particles Background ─────────────────────────────────────────────────────

function initParticles() {
  const container = document.getElementById('particles');
  if (!container) return;

  const particleCount = 30;

  for (let i = 0; i < particleCount; i++) {
    const particle = document.createElement('div');
    const size = Math.random() * 3 + 1;
    const x = Math.random() * 100;
    const y = Math.random() * 100;
    const duration = Math.random() * 20 + 10;
    const delay = Math.random() * 10;
    const opacity = Math.random() * 0.3 + 0.1;

    particle.style.cssText = `
      position: absolute;
      width: ${size}px;
      height: ${size}px;
      background: rgba(255, 51, 51, ${opacity});
      border-radius: 50%;
      left: ${x}%;
      top: ${y}%;
      animation: particleFloat ${duration}s ease-in-out ${delay}s infinite;
      pointer-events: none;
    `;

    container.appendChild(particle);
  }

  // Add particle animation keyframes
  const style = document.createElement('style');
  style.textContent = `
    @keyframes particleFloat {
      0%, 100% { transform: translate(0, 0) scale(1); opacity: 0.3; }
      25% { transform: translate(${rand(-30, 30)}px, ${rand(-30, 30)}px) scale(1.2); opacity: 0.6; }
      50% { transform: translate(${rand(-20, 20)}px, ${rand(-50, 10)}px) scale(0.8); opacity: 0.2; }
      75% { transform: translate(${rand(-40, 40)}px, ${rand(-20, 20)}px) scale(1.1); opacity: 0.5; }
    }
  `;
  document.head.appendChild(style);
}

function rand(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// ─── Server Status ────────────────────────────────────────────────────────────

async function fetchServerStatus() {
  try {
    const res = await fetch('/api/server/status');
    const data = await res.json();
    const onlineEl = document.getElementById('online-count');
    if (onlineEl && data.players) {
      onlineEl.textContent = `${data.players.current}/${data.players.max}`;
    }
  } catch (e) {
    // Server status unavailable — leave as default
  }
}

// ─── Auth Button ──────────────────────────────────────────────────────────────

const authBtn = document.getElementById('nav-auth-btn');
if (authBtn) {
  authBtn.addEventListener('click', (e) => {
    e.preventDefault();
    // In production, this would open a modal or redirect to auth page
    alert('Авторизация доступна через лаунчер War Project');
  });
}
