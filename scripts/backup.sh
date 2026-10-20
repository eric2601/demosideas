#!/usr/bin/env bash
# Respaldo diario de PostgreSQL (formato custom). Ejemplo cron:  0 2 * * *  /ruta/scripts/backup.sh
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f .env ]; then set -a; . ./.env; set +a; fi
DEST="${BACKUP_DIR:-./backups}"; KEEP="${BACKUP_KEEP:-14}"
mkdir -p "$DEST"
FILE="$DEST/almacen-$(date +%Y%m%d-%H%M%S).dump"
if command -v docker >/dev/null && docker compose ps --status running db >/dev/null 2>&1 && [ -n "$(docker compose ps -q db 2>/dev/null)" ]; then
  docker compose exec -T db pg_dump -U almacen --format=custom almacen > "$FILE"
else
  pg_dump --format=custom --no-owner --file="$FILE" "${DATABASE_URL:?Define DATABASE_URL}"
fi
ls -1t "$DEST"/almacen-*.dump | tail -n +$((KEEP+1)) | xargs -r rm --
echo "Respaldo creado: $FILE"
