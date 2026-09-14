# Matrix Camera self-hosted server

Recommended OS: **Debian 13 Stable**. It is a free, lightweight and stable base for a long-running self-hosted Docker server.

## Components

- signaling service: `signaling/server.js`
- Docker Compose: `docker-compose.yml`
- planned WebRTC media path: direct peer connection first, self-hosted TURN when NAT traversal requires a relay

## Installation

1. Install Debian 13 on your own PC, mini-PC, NAS or server.
2. Install Docker Engine and the Compose plugin.
3. Copy this `server/` directory to `/opt/matrix-camera`.
4. Run `docker compose up -d`.
5. Check `http://127.0.0.1:8080/health` locally.

For Internet access, expose only the required HTTPS/signaling endpoint and add a self-hosted TURN server. WebRTC documentation recommends TURN when a direct peer connection is not possible.

No camera video is stored by the base signaling service.
