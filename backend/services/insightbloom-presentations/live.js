const { WebSocketServer } = require('ws');

const PRESENTER_WS_RE = /^\/api\/v1\/conferences\/([^/]+)\/presentation\/ws\/presenter$/;
const AUDIENCE_WS_RE = /^\/api\/v1\/conferences\/([^/]+)\/presentation\/ws\/audience$/;

/**
 * Sincronización en vivo del slide actual entre presentador y audiencia.
 * Estado en memoria por conferencia (no persistente, no requiere multi-réplica
 * hoy ya que el servicio corre como un solo pod).
 */
const HEARTBEAT_INTERVAL_MS = 30000;

function attachLiveSync(server, { usersUrl }) {
  const wss = new WebSocketServer({ noServer: true });
  const rooms = new Map();

  wss.on('connection', (ws) => {
    ws.isAlive = true;
    ws.on('pong', () => {
      ws.isAlive = true;
    });
  });

  const heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      if (ws.isAlive === false) {
        ws.terminate();
        continue;
      }
      ws.isAlive = false;
      ws.ping();
    }
  }, HEARTBEAT_INTERVAL_MS);

  server.on('close', () => clearInterval(heartbeat));

  function room(conferenceId) {
    if (!rooms.has(conferenceId)) {
      rooms.set(conferenceId, { currentHash: null, presenters: new Set(), audience: new Set() });
    }
    return rooms.get(conferenceId);
  }

  function broadcastCount(conferenceId) {
    const r = room(conferenceId);
    const payload = JSON.stringify({ type: 'count', count: r.audience.size });
    for (const ws of r.presenters) {
      if (ws.readyState === ws.OPEN) ws.send(payload);
    }
  }

  async function isOrganizerOrAdmin(token) {
    if (!token) return false;
    try {
      const res = await fetch(`${usersUrl}/api/v1/auth/validate`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) return false;
      const body = await res.json();
      const role = (body && body.data && body.data.role) || '';
      return !!(body && body.data && body.data.valid) && (role.includes('organizer') || role.includes('admin'));
    } catch {
      return false;
    }
  }

  server.on('upgrade', async (req, socket, head) => {
    let url;
    try {
      url = new URL(req.url, 'http://localhost');
    } catch {
      socket.destroy();
      return;
    }

    const presenterMatch = url.pathname.match(PRESENTER_WS_RE);
    if (presenterMatch) {
      const conferenceId = presenterMatch[1];
      const token = url.searchParams.get('token');
      const authorized = await isOrganizerOrAdmin(token);
      if (!authorized) {
        socket.destroy();
        return;
      }
      wss.handleUpgrade(req, socket, head, (ws) => {
        const r = room(conferenceId);
        r.presenters.add(ws);
        ws.send(JSON.stringify({ type: 'count', count: r.audience.size }));

        ws.on('message', (raw) => {
          let msg;
          try {
            msg = JSON.parse(raw);
          } catch {
            return;
          }
          if (msg && msg.type === 'slide' && typeof msg.hash === 'string') {
            r.currentHash = msg.hash;
            const payload = JSON.stringify({ type: 'slide', hash: msg.hash });
            for (const a of r.audience) {
              if (a.readyState === a.OPEN) a.send(payload);
            }
          }
        });
        ws.on('close', () => {
          r.presenters.delete(ws);
        });
      });
      return;
    }

    const audienceMatch = url.pathname.match(AUDIENCE_WS_RE);
    if (audienceMatch) {
      const conferenceId = audienceMatch[1];
      wss.handleUpgrade(req, socket, head, (ws) => {
        const r = room(conferenceId);
        r.audience.add(ws);
        if (r.currentHash) {
          ws.send(JSON.stringify({ type: 'slide', hash: r.currentHash }));
        }
        broadcastCount(conferenceId);

        ws.on('close', () => {
          r.audience.delete(ws);
          broadcastCount(conferenceId);
        });
      });
      return;
    }

    socket.destroy();
  });
}

module.exports = { attachLiveSync };
