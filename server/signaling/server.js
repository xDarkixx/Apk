const http = require('http');
const port = Number(process.env.PORT || 8080);
const host = process.env.HOST || '0.0.0.0';
const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, {'content-type': 'application/json'});
    return res.end(JSON.stringify({ok: true}));
  }
  res.writeHead(404, {'content-type': 'application/json'});
  res.end(JSON.stringify({error: 'not_found'}));
});
server.listen(port, host, () => console.log(`listening on ${host}:${port}`));
