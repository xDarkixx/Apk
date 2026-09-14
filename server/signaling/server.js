const http = require('http');
const WebSocket = require('ws');

const port = Number(process.env.PORT || 8080);
const host = process.env.HOST || '0.0.0.0';
const rooms = new Map();

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, {'content-type': 'application/json'});
    return res.end(JSON.stringify({ok: true, service: 'matrix-camera-signaling'}));
  }
  res.writeHead(404, {'content-type': 'application/json'});
  res.end(JSON.stringify({error: 'not_found'}));
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
    try { msg = JSON.parse(raw.toString()); } catch { return; }
    if (msg.type === 'join' && typeof msg.room === 'string' && msg.room.length >= 8) {
      room = msg.room.slice(0, 128);
      if (!rooms.has(room)) rooms.set(room, new Set());
      const peers = rooms.get(room);
      if (peers.size >= 2) return socket.send(JSON.stringify({type: 'error', error: 'room_full'}));
      peers.add(socket);
      socket.send(JSON.stringify({type: 'joined', peers: peers.size}));
      if (peers.size === 2) broadcast(room, socket, JSON.stringify({type: 'peer_ready'}));
      return;
    }
    if (!room) return;
    broadcast(room, socket, JSON.stringify(msg));
  });
  socket.on('close', () => {
    if (!room) return;
    const peers = rooms.get(room);
    if (!peers) return;
    peers.delete(socket);
    broadcast(room, socket, JSON.stringify({type: 'peer_left'}));
    if (peers.size === 0) rooms.delete(room);
  });
});

server.listen(port, host, () => console.log(`Matrix Camera signaling listening on ${host}:${port}`));
