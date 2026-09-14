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
apt-get install -y ca-certificates curl git docker.io docker-compose-plugin
systemctl enable --now docker

mkdir -p "$INSTALL_DIR"
cp -a "$SCRIPT_DIR"/. "$INSTALL_DIR"/
cd "$INSTALL_DIR"

docker compose up -d

echo
echo "Matrix Camera Server läuft."
echo "Health: http://127.0.0.1:8080/health"
echo "Signaling: ws://<SERVER-IP>:8080/signal"
echo "Für Internetbetrieb HTTPS/WSS und einen eigenen TURN-Server konfigurieren."
