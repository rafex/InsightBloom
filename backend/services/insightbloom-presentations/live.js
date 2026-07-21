const crypto = require('crypto');
const { WebSocketServer } = require('ws');
const { connect, StringCodec } = require('nats');

const PRESENTER_WS_RE = /^\/api\/v1\/conferences\/([^/]+)\/presentation\/ws\/presenter$/;
const AUDIENCE_WS_RE = /^\/api\/v1\/conferences\/([^/]+)\/presentation\/ws\/audience$/;
const REMOTE_WS_RE = /^\/api\/v1\/conferences\/([^/]+)\/presentation\/ws\/remote$/;
const NAV_DIRECTIONS = new Set(['next', 'prev']);
const REMOTE_TOKEN_TTL_MS = 6 * 60 * 60 * 1000; // 6 horas

/**
 * Sincronización en vivo del slide actual entre presentador y audiencia.
 *
 * Cada pod mantiene un caché en memoria (Map por conferencia) y reparte
 * directo a sus sockets locales sin depender de NATS para eso — eso sigue
 * funcionando igual que antes con un solo pod. Pero el hash "fuente de verdad"
 * se guarda en un KV de NATS JetStream (bucket `presentation_state`): así,
 * si el pod de insightbloom-presentations se reinicia (deploy) o hay 2+
 * réplicas, cualquier pod puede recuperar el slide actual al conectar en vez
 * de asumir "sin presentación todavía" y mandar a la audiencia de vuelta al
 * inicio. Si NATS/JetStream no está disponible, cae a memoria local (modo
 * "un solo pod", igual que antes).
 */
const HEARTBEAT_INTERVAL_MS = 30000;
const HOSTNAME = process.env.HOSTNAME || 'local';
const KV_BUCKET = 'presentation_state';

const rooms = new Map();
let usersUrlRef = null;
let internalApiKeyRef = null;
let nc = null; // conexión NATS (null si no está disponible)
let kv = null; // KV de JetStream para el hash actual por conferencia (null si no está disponible)
const sc = StringCodec();

async function kvReadHash(conferenceId) {
  if (!kv) return null;
  try {
    const entry = await kv.get(conferenceId);
    return entry ? sc.decode(entry.value) : null;
  } catch {
    return null;
  }
}

function kvWriteHash(conferenceId, hash) {
  if (!kv) return;
  kv.put(conferenceId, sc.encode(hash)).catch((err) => {
    console.error('kv_put_failed', conferenceId, err.message);
  });
}

function room(conferenceId) {
  if (!rooms.has(conferenceId)) {
    rooms.set(conferenceId, {
      currentHash: null,
      globalAudienceCount: 0,
      presenters: new Set(),
      audience: new Set(),
      remotes: new Set(),
    });
  }
  return rooms.get(conferenceId);
}

function audienceCount(r) {
  return nc ? r.globalAudienceCount : r.audience.size;
}

function broadcastCount(conferenceId) {
  const r = room(conferenceId);
  const payload = JSON.stringify({ type: 'count', count: audienceCount(r) });
  for (const ws of r.presenters) {
    if (ws.readyState === ws.OPEN) ws.send(payload);
  }
}

async function isOrganizerOrAdmin(token) {
  if (!token || !usersUrlRef) return false;
  try {
    const res = await fetch(`${usersUrlRef}/api/v1/auth/validate`, {
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

async function hasConferenceAccess(conferenceId, token) {
  if (!token || !usersUrlRef) return false;
  try {
    const res = await fetch(`${usersUrlRef}/api/v1/conferences/${conferenceId}/access`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) return false;
    const body = await res.json();
    return body?.data?.hasAccess === true;
  } catch {
    return false;
  }
}

// Token de control remoto autoverificable (HMAC), sin estado compartido entre
// pods: cualquier pod puede emitirlo o validarlo con la misma clave
// (INTERNAL_API_KEY, ya es un secreto compartido en el cluster).
function signRemoteToken(conferenceId) {
  const payload = Buffer.from(JSON.stringify({ c: conferenceId, exp: Date.now() + REMOTE_TOKEN_TTL_MS }))
    .toString('base64url');
  const sig = crypto.createHmac('sha256', internalApiKeyRef || '').update(payload).digest('base64url');
  return `${payload}.${sig}`;
}

function verifyRemoteToken(conferenceId, token) {
  if (!token || typeof token !== 'string' || !token.includes('.')) return false;
  const [payload, sig] = token.split('.');
  const expectedSig = crypto.createHmac('sha256', internalApiKeyRef || '').update(payload).digest('base64url');
  if (sig !== expectedSig) return false;
  try {
    const { c, exp } = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
    return c === conferenceId && typeof exp === 'number' && exp > Date.now();
  } catch {
    return false;
  }
}

async function issueRemoteToken(conferenceId, organizerToken) {
  const authorized = await isOrganizerOrAdmin(organizerToken);
  if (!authorized) return null;
  return signRemoteToken(conferenceId);
}

async function connectNats(natsUrl, natsToken) {
  if (!natsUrl) return null;
  try {
    const conn = await connect({ servers: natsUrl, token: natsToken || undefined });
    console.log('insightbloom-presentations: conectado a NATS en', natsUrl);
    conn.closed().then((err) => {
      console.error('insightbloom-presentations: conexión a NATS cerrada', err || '');
      nc = null;
      kv = null;
    });
    return conn;
  } catch (err) {
    console.error('insightbloom-presentations: no se pudo conectar a NATS, sigue en modo local:', err.message);
    return null;
  }
}

async function openKv(conn) {
  if (!conn) return null;
  try {
    const js = conn.jetstream();
    const bucket = await js.views.kv(KV_BUCKET, { history: 1 });
    console.log('insightbloom-presentations: KV JetStream listo:', KV_BUCKET);
    return bucket;
  } catch (err) {
    console.error('insightbloom-presentations: JetStream KV no disponible, sigue sin persistencia entre reinicios:', err.message);
    return null;
  }
}

function publishBestEffort(subject, payload) {
  if (!nc) return;
  try {
    nc.publish(subject, sc.encode(JSON.stringify(payload)));
  } catch (err) {
    console.error('nats_publish_failed', subject, err.message);
  }
}

async function subscribeWildcards() {
  if (!nc) return;

  (async () => {
    const sub = nc.subscribe('presentation.*.slide');
    for await (const msg of sub) {
      try {
        const { conferenceId, hash, origin } = JSON.parse(sc.decode(msg.data));
        if (origin === HOSTNAME) continue; // ya se entregó por el camino local
        const r = room(conferenceId);
        r.currentHash = hash;
        const payload = JSON.stringify({ type: 'slide', hash });
        for (const a of r.audience) {
          if (a.readyState === a.OPEN) a.send(payload);
        }
      } catch { /* mensaje malformado, ignorar */ }
    }
  })();

  (async () => {
    const sub = nc.subscribe('presentation.*.nav');
    for await (const msg of sub) {
      try {
        const { conferenceId, direction, origin } = JSON.parse(sc.decode(msg.data));
        if (origin === HOSTNAME) continue;
        const r = room(conferenceId);
        const payload = JSON.stringify({ type: 'nav', direction });
        for (const p of r.presenters) {
          if (p.readyState === p.OPEN) p.send(payload);
        }
      } catch { /* ignorar */ }
    }
  })();

  (async () => {
    const sub = nc.subscribe('presentation.*.audience-delta');
    for await (const msg of sub) {
      try {
        const { conferenceId, delta } = JSON.parse(sc.decode(msg.data));
        const r = room(conferenceId);
        r.globalAudienceCount = Math.max(0, r.globalAudienceCount + delta);
        broadcastCount(conferenceId);
      } catch { /* ignorar */ }
    }
  })();
}

function attachLiveSync(server, { usersUrl, natsUrl, natsToken, internalApiKey }) {
  usersUrlRef = usersUrl;
  internalApiKeyRef = internalApiKey;
  const wss = new WebSocketServer({ noServer: true });

  connectNats(natsUrl, natsToken).then(async (conn) => {
    nc = conn;
    kv = await openKv(conn);
    subscribeWildcards();
  });

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
        ws.send(JSON.stringify({ type: 'count', count: audienceCount(r) }));

        ws.on('message', (raw) => {
          let msg;
          try {
            msg = JSON.parse(raw);
          } catch {
            return;
          }
          if (msg && msg.type === 'slide' && typeof msg.hash === 'string') {
            r.currentHash = msg.hash;
            kvWriteHash(conferenceId, msg.hash);
            const payload = JSON.stringify({ type: 'slide', hash: msg.hash });
            for (const a of r.audience) {
              if (a.readyState === a.OPEN) a.send(payload);
            }
            publishBestEffort('presentation.' + conferenceId + '.slide',
              { conferenceId, hash: msg.hash, origin: HOSTNAME });
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
      const token = url.searchParams.get('ib_token');
      if (!await hasConferenceAccess(conferenceId, token)) {
        socket.destroy();
        return;
      }
      wss.handleUpgrade(req, socket, head, async (ws) => {
        const r = room(conferenceId);
        r.audience.add(ws);
        // Si este pod no tiene el hash en memoria (recién reiniciado, o nunca
        // recibió el evento local), lo recupera del KV de NATS antes de asumir
        // que no hay presentación en curso.
        if (!r.currentHash) {
          r.currentHash = await kvReadHash(conferenceId);
        }
        if (r.currentHash && ws.readyState === ws.OPEN) {
          ws.send(JSON.stringify({ type: 'slide', hash: r.currentHash }));
        }
        publishBestEffort('presentation.' + conferenceId + '.audience-delta', { conferenceId, delta: 1 });
        if (!nc) broadcastCount(conferenceId);

        ws.on('close', () => {
          r.audience.delete(ws);
          publishBestEffort('presentation.' + conferenceId + '.audience-delta', { conferenceId, delta: -1 });
          if (!nc) broadcastCount(conferenceId);
        });
      });
      return;
    }

    const remoteMatch = url.pathname.match(REMOTE_WS_RE);
    if (remoteMatch) {
      const conferenceId = remoteMatch[1];
      const token = url.searchParams.get('token');
      if (!verifyRemoteToken(conferenceId, token)) {
        socket.destroy();
        return;
      }
      wss.handleUpgrade(req, socket, head, (ws) => {
        const r = room(conferenceId);
        r.remotes.add(ws);

        ws.on('message', (raw) => {
          let msg;
          try {
            msg = JSON.parse(raw);
          } catch {
            return;
          }
          if (msg && msg.type === 'nav' && NAV_DIRECTIONS.has(msg.direction)) {
            const payload = JSON.stringify({ type: 'nav', direction: msg.direction });
            for (const p of r.presenters) {
              if (p.readyState === p.OPEN) p.send(payload);
            }
            publishBestEffort('presentation.' + conferenceId + '.nav',
              { conferenceId, direction: msg.direction, origin: HOSTNAME });
          }
        });
        ws.on('close', () => {
          r.remotes.delete(ws);
        });
      });
      return;
    }

    socket.destroy();
  });
}

module.exports = { attachLiveSync, issueRemoteToken };
