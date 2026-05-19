# War Project Site

Website and API backend for the War Project Minecraft server.

## Features

- Landing page with server info, features, factions
- REST API for authentication (register/login)
- JWT-based sessions shared with the launcher
- SQLite database for user accounts
- Server status endpoint

## Quick Start

```bash
npm install
npm start
```

Server runs on `http://localhost:4000`.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register new account |
| POST | `/api/auth/login` | Login |
| GET | `/api/auth/profile` | Get profile (auth required) |
| GET | `/api/server/status` | Server status (public) |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `4000` | Server port |
| `JWT_SECRET` | `warproject-secret-...` | JWT signing secret (change in production!) |

## Production

1. Set `JWT_SECRET` to a secure random string
2. Consider using PostgreSQL instead of SQLite for high traffic
3. Add rate limiting and HTTPS (nginx reverse proxy recommended)
