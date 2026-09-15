# Matrix Camera

Self-hosted Android camera system for **explicitly enabled** remote camera viewing.

## Status

The project is configured for Android API 26+ and uses WebRTC for the camera stream. The repository includes a self-hosted signaling server and a self-hosted coturn TURN relay.

## Architecture

- Android camera phone -> WebRTC video
- Android viewer phone -> WebRTC receiver
- Your own signaling server -> only exchanges connection/session messages
- Your own TURN server -> relay fallback when a direct WebRTC connection is not possible
- No third-party camera cloud is required
- Server base: Debian 13 + Docker

The Android build uses WebRTC `150.7871.01` from Maven Central and NanoHTTPD `2.3.1` for the optional MJPEG components. citeturn1search1turn1search6

## Server

The complete self-hosted server is in `server/`.

Automatic Debian installation:

```bash
sudo bash server/install-debian.sh
```

The installer installs Docker, creates a random TURN password, writes `/opt/matrix-camera/.env`, downloads the required images, and starts the signaling and TURN containers.

### Internet setup

Set `TURN_HOST` in `/opt/matrix-camera/.env` to a public DNS name or public IP reachable by both Android devices. Open/forward these ports to the server:

- TCP/UDP `3478`
- UDP `49152-49252`
- TCP `8080` for signaling, or put signaling behind your own HTTPS/WSS reverse proxy

The Android client receives the TURN configuration from the signaling server after joining the room. Use `wss://.../signal` in production so TURN credentials are not sent over an unencrypted WebSocket connection.

## Android

The camera phone must explicitly grant `CAMERA` permission and press **KAMERA**. The viewer must enter the same signaling URL and an identical room code of at least 8 characters, then press **VIEWER**.

The app does not provide hidden camera activation. Background camera use must follow Android's foreground-service and visible-indicator requirements.

## Build

GitHub Actions builds the debug APK on pushes and pull requests:

```bash
gradle assembleDebug
```

The generated APK is under `app/build/outputs/apk/debug/`.

## Health check

After the server starts:

```bash
curl http://127.0.0.1:8080/health
```

A healthy server reports `ok: true` and whether TURN credentials are configured.
