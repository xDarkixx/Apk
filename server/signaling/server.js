const http = require('http');
const WebSocket = require('ws');

const port = Number(process.env.PORT || 8080);
const host = process.env.HOST || '0.0.0.0';
const turnHost = process.env.TURN_HOST || '';
const turnPort = Number(process.env.TURN_PORT || 3478);
const turnUser = process.env.TURN_USER || 'matrix';
const turnPassword = process.env.TURN_PASSWORD || '';
const rooms = new Map();

function iceConfig() {
  if (!turnHost || !turnPassword) return { iceServers: [] };
  return {
    iceServers: [
      { urls: [`turn:${turnHost}:${turnPort}?transport=udp`, `turn:${turnHost}:${turnPort}?transport=tcp`], username: turnUser, credential: turnPassword },
      { urls: [`stun:${turnHost}:${turnPort}`] }
    ]
  };
}

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    return res.end(JSON.stringify({ ok: true, service: 'matrix-camera-signaling', turnConfigured: Boolean(turnHost && turnPassword) }));
  }
  res.writeHead(404, { 'content-type': 'application/json' });
  res.end(JSON.stringify({ error: 'not_found' }));
});

const wss = new WebSocket.Server({ server, path: '/signal' });

function broadcast(room, sender, message) {
  const peers = rooms.get(room);
  if (!peers) return;
  for (const peer of peers) {
    if (peer !== sender && peer.readyState === WebSocket.OPEN) peer.send(message);
  }
}

wss.on('connection', (socket) => {
  let room = null;

  socket.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      socket.send(JSON.stringify({ type: 'error', error: 'invalid_json' }));
      return;
    }

    if (msg.type === 'join') {
      if (typeof msg.room !== 'string' || msg.room.length < 8 || msg.room.length > 128) {
        socket.send(JSON.stringify({ type: 'error', error: 'invalid_room' }));
        return;
      }
      if (room) {
        socket.send(JSON.stringify({ type: 'error', error: 'already_joined' }));
        return;
      }

      room = msg.room;
      if (!rooms.has(room)) rooms.set(room, new Set());
      const peers = rooms.get(room);
      if (peers.size >= 2) {
        socket.send(JSON.stringify({ type: 'error', error: 'room_full' }));
        room = null;
        return;
      }

      peers.add(socket);
      socket.send(JSON.stringify({ type: 'joined', peers: peers.size, ice: iceConfig() }));
      if (peers.size === 2) broadcast(room, socket, JSON.stringify({ type: 'peer_ready' }));
      return;
    }

    if (!room) {
      socket.send(JSON.stringify({ type: 'error', error: 'join_required' }));
      return;
    }

    if (msg.type === 'offer' || msg.type === 'answer' || msg.type === 'ice') {
      broadcast(room, socket, JSON.stringify(msg));
    }
  });

  socket.on('close', () => {
    if (!room) return;
    const peers = rooms.get(room);
    if (!peers) return;
    peers.delete(socket);
    broadcast(room, socket, JSON.stringify({ type: 'peer_left' }));
    if (peers.size === 0) rooms.delete(room);
  });
});

server.listen(port, host, () => {
  console.log(`Matrix Camera signaling listening on ${host}:${port}`);
});
