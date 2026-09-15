#!/usr/bin/env bash
set -euo pipefail

INSTALL_DIR="/opt/matrix-camera"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Bitte als root ausführen: sudo bash server/install-debian.sh"
  exit 1
fi

if ! grep -qi '^ID=debian' /etc/os-release; then
  echo "Dieses Installationsskript ist für Debian vorgesehen."
  exit 1
fi

apt-get update
apt-get install -y ca-certificates curl git openssl docker.io docker-compose-plugin
systemctl enable --now docker

mkdir -p "$INSTALL_DIR"
cp -a "$SCRIPT_DIR"/. "$INSTALL_DIR"/
cd "$INSTALL_DIR"

if [[ ! -f .env ]]; then
  detected_host="$(hostname -I 2>/dev/null | awk '{print $1}')"
  detected_host="${detected_host:-127.0.0.1}"
  turn_password="$(openssl rand -hex 24)"
  cat > .env <<EOF
# Public DNS name or public IP address reachable by both Android devices.
TURN_HOST=${TURN_HOST:-$detected_host}
TURN_PORT=3478
TURN_USER=${TURN_USER:-matrix}
TURN_PASSWORD=${TURN_PASSWORD:-$turn_password}
TURN_REALM=${TURN_REALM:-matrix-camera.local}
EOF
  chmod 600 .env
fi

if ! grep -q '^TURN_HOST=' .env || ! grep -q '^TURN_PASSWORD=' .env; then
  echo "Fehler: .env enthält keine vollständige TURN-Konfiguration."
  exit 1
fi

# Pull images before starting so installation errors appear immediately.
docker compose pull
docker compose up -d

echo
echo "Matrix Camera Server läuft."
echo "Installation: $INSTALL_DIR"
echo "Health: http://127.0.0.1:8080/health"
echo "Signaling: ws://<SERVER-IP>:8080/signal"
echo "TURN: turn://$(grep '^TURN_HOST=' .env | cut -d= -f2):3478"
echo
echo "Wichtig: Für Internetbetrieb muss TURN_HOST auf eine von außen erreichbare öffentliche IP oder DNS-Adresse zeigen."
echo "Für produktiven Betrieb sollte das Signaling über WSS/HTTPS hinter einem Reverse Proxy laufen."
