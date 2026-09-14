# Matrix Camera

Self-hosted Android camera system for **explicitly enabled** remote camera viewing.

## Architecture

- Android camera phone -> WebRTC video
- Android viewer phone -> WebRTC receiver
- Your own signaling server -> only exchanges connection/session messages
- Your own TURN server -> relay fallback when a direct WebRTC connection is not possible
- No third-party camera cloud is required
- Server base: Debian 13 + Docker

WebRTC uses ICE/STUN/TURN to establish connections across different networks; signaling is a separate service. citeturn0search1turn0search0

## Server

The complete self-hosted server is in `server/`.

Automatic Debian installation:

```bash
sudo bash server/install-debian.sh
```

It installs Docker, starts the signaling and TURN containers, and configures them to restart automatically.

## Important

The camera is never designed for hidden activation. The Android user must grant camera permission and explicitly start the camera. Android background-camera operation must follow Android's foreground-service rules and visible system indication.

## Build

GitHub Actions automatically builds the Android debug APK after repository changes and uploads it as an artifact.
