#!/usr/bin/env bash
# WarProject — incremental backup helper.
# Usage: backup.sh [SERVER_DIR] [BACKUP_DIR] [RETAIN_DAYS]
# Defaults: ./server, ./backups, 7
#
# Env (optional, for live save flush via RCON):
#   RCON_HOST=127.0.0.1
#   RCON_PORT=25575
#   RCON_PASSWORD=...

set -euo pipefail

SERVER_DIR="${1:-./server}"
BACKUP_DIR="${2:-./backups}"
RETAIN_DAYS="${3:-7}"

if [ ! -d "${SERVER_DIR}" ]; then
  echo "Server dir not found: ${SERVER_DIR}" >&2
  exit 1
fi

mkdir -p "${BACKUP_DIR}"

TS="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="${BACKUP_DIR}/warproject-${TS}.tar.zst"

echo "[backup] writing ${OUT}"

# Flush MC chunk writes if RCON is available (mcrcon binary required).
if command -v mcrcon >/dev/null 2>&1 && [ -n "${RCON_PASSWORD:-}" ]; then
  echo "[backup] flushing world via RCON"
  mcrcon -H "${RCON_HOST:-127.0.0.1}" -P "${RCON_PORT:-25575}" -p "${RCON_PASSWORD}" \
    "save-off" "save-all flush" || true
  trap 'mcrcon -H "${RCON_HOST:-127.0.0.1}" -P "${RCON_PORT:-25575}" -p "${RCON_PASSWORD}" "save-on" || true' EXIT
fi

# Pick available worlds.
WORLDS=()
for w in world world_nether world_the_end; do
  [ -d "${SERVER_DIR}/${w}" ] && WORLDS+=("${w}")
done

EXTRA=()
[ -f "${SERVER_DIR}/warproject.db" ] && EXTRA+=("warproject.db")
[ -f "${SERVER_DIR}/warproject.db-journal" ] && EXTRA+=("warproject.db-journal")
[ -d "${SERVER_DIR}/config" ] && EXTRA+=("config")
[ -f "${SERVER_DIR}/whitelist.json" ] && EXTRA+=("whitelist.json")
[ -f "${SERVER_DIR}/ops.json" ] && EXTRA+=("ops.json")
[ -f "${SERVER_DIR}/banned-players.json" ] && EXTRA+=("banned-players.json")
[ -f "${SERVER_DIR}/banned-ips.json" ] && EXTRA+=("banned-ips.json")

if command -v zstd >/dev/null 2>&1; then
  tar --use-compress-program="zstd -T0 -19" \
      -cf "${OUT}" \
      -C "${SERVER_DIR}" \
      "${WORLDS[@]}" "${EXTRA[@]}"
else
  OUT="${OUT%.zst}.gz"
  tar -czf "${OUT}" -C "${SERVER_DIR}" "${WORLDS[@]}" "${EXTRA[@]}"
fi

# Rotate older backups.
echo "[backup] rotating archives older than ${RETAIN_DAYS} days"
find "${BACKUP_DIR}" -name "warproject-*.tar.*" -mtime "+${RETAIN_DAYS}" -print -delete

COUNT=$(find "${BACKUP_DIR}" -name "warproject-*.tar.*" | wc -l)
SIZE=$(du -sh "${OUT}" | awk '{print $1}')
echo "[backup] OK: ${OUT} (${SIZE}); ${COUNT} archives kept in ${BACKUP_DIR}"
