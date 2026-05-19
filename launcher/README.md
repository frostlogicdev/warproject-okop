# War Project Launcher

Electron-based launcher for the War Project Minecraft server.

## Features

- Account login/registration via the War Project API
- Game file management and auto-updates
- Settings (RAM, Java path, resolution)
- Beautiful animated UI with dark military theme

## Development

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
```

Produces an installer in `dist-electron/`.

## Architecture

- **Electron** — desktop app shell
- **React + Vite** — renderer UI
- **Framer Motion** — animations
- **War Project API** — auth backend (site server on port 4000)
