const express = require('express');
const cors = require('cors');
const path = require('path');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const fs = require('fs');
const initSqlJs = require('sql.js');

const app = express();
const PORT = process.env.PORT || 4000;
const JWT_SECRET = process.env.JWT_SECRET || 'warproject-secret-change-in-production';
const SALT_ROUNDS = 10;
const DB_PATH = path.join(__dirname, 'warproject-web.db');

let db;

// ─── Database Setup ───────────────────────────────────────────────────────────

async function initDatabase() {
  const SQL = await initSqlJs();

  if (fs.existsSync(DB_PATH)) {
    const buffer = fs.readFileSync(DB_PATH);
    db = new SQL.Database(buffer);
  } else {
    db = new SQL.Database();
  }

  db.run(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT NOT NULL UNIQUE,
      email TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
      last_login INTEGER
    )
  `);

  saveDb();
}

function saveDb() {
  const data = db.export();
  const buffer = Buffer.from(data);
  fs.writeFileSync(DB_PATH, buffer);
}

// ─── Middleware ───────────────────────────────────────────────────────────────

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// ─── Auth Middleware ──────────────────────────────────────────────────────────

function authMiddleware(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ message: 'Требуется авторизация' });
  }
  try {
    const token = authHeader.split(' ')[1];
    const decoded = jwt.verify(token, JWT_SECRET);
    req.user = decoded;
    next();
  } catch (e) {
    return res.status(401).json({ message: 'Недействительный токен' });
  }
}

// ─── API Routes ───────────────────────────────────────────────────────────────

// Register
app.post('/api/auth/register', async (req, res) => {
  try {
    const { username, email, password } = req.body;

    if (!username || !email || !password) {
      return res.status(400).json({ message: 'Все поля обязательны' });
    }
    if (username.length < 3 || username.length > 16) {
      return res.status(400).json({ message: 'Имя пользователя: 3-16 символов' });
    }
    if (!/^[A-Za-z0-9_]+$/.test(username)) {
      return res.status(400).json({ message: 'Имя может содержать только буквы, цифры и _' });
    }
    if (password.length < 6) {
      return res.status(400).json({ message: 'Пароль минимум 6 символов' });
    }

    const existing = db.exec('SELECT id FROM users WHERE username = ? OR email = ?', [username, email]);
    if (existing.length > 0 && existing[0].values.length > 0) {
      return res.status(409).json({ message: 'Пользователь или email уже зарегистрирован' });
    }

    const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
    db.run('INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)', [username, email, passwordHash]);
    saveDb();

    const inserted = db.exec('SELECT last_insert_rowid() as id');
    const userId = inserted[0].values[0][0];

    const token = jwt.sign({ id: userId, username }, JWT_SECRET, { expiresIn: '7d' });

    res.status(201).json({ username, token });
  } catch (e) {
    console.error('Register error:', e);
    res.status(500).json({ message: 'Внутренняя ошибка сервера' });
  }
});

// Login
app.post('/api/auth/login', async (req, res) => {
  try {
    const { username, password } = req.body;

    if (!username || !password) {
      return res.status(400).json({ message: 'Введите имя и пароль' });
    }

    const result = db.exec('SELECT id, username, password_hash FROM users WHERE username = ?', [username]);
    if (result.length === 0 || result[0].values.length === 0) {
      return res.status(401).json({ message: 'Неверное имя пользователя или пароль' });
    }

    const row = result[0].values[0];
    const user = { id: row[0], username: row[1], password_hash: row[2] };

    const valid = await bcrypt.compare(password, user.password_hash);
    if (!valid) {
      return res.status(401).json({ message: 'Неверное имя пользователя или пароль' });
    }

    db.run("UPDATE users SET last_login = strftime('%s', 'now') WHERE id = ?", [user.id]);
    saveDb();

    const token = jwt.sign({ id: user.id, username: user.username }, JWT_SECRET, { expiresIn: '7d' });

    res.json({ username: user.username, token });
  } catch (e) {
    console.error('Login error:', e);
    res.status(500).json({ message: 'Внутренняя ошибка сервера' });
  }
});

// Get profile
app.get('/api/auth/profile', authMiddleware, (req, res) => {
  const result = db.exec('SELECT id, username, email, created_at, last_login FROM users WHERE id = ?', [req.user.id]);
  if (result.length === 0 || result[0].values.length === 0) {
    return res.status(404).json({ message: 'Пользователь не найден' });
  }
  const row = result[0].values[0];
  const cols = result[0].columns;
  const user = {};
  cols.forEach((col, i) => { user[col] = row[i]; });
  res.json(user);
});

// Server status (public)
app.get('/api/server/status', (req, res) => {
  res.json({
    online: true,
    players: { current: 0, max: 50 },
    version: '1.21.1',
    modVersion: '3.0.0'
  });
});

// ─── SPA Fallback ─────────────────────────────────────────────────────────────

app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// ─── Start ────────────────────────────────────────────────────────────────────

initDatabase().then(() => {
  app.listen(PORT, () => {
    console.log(`[WarProject Site] Running on http://localhost:${PORT}`);
  });
}).catch(err => {
  console.error('Failed to initialize database:', err);
  process.exit(1);
});
