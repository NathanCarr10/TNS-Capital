const express = require('express');
const jwt = require('jsonwebtoken');

const app = express();
app.use(express.json());
// OAuth2 token requests (e.g. from Swagger UI's Authorize dialog) are form-encoded
app.use(express.urlencoded({ extended: false }));

// Swagger UI is served by the mission service (port 3000) but calls /token here
// (port 4000) from the browser, so this origin must be allowed explicitly.
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || 'http://localhost:3000,http://127.0.0.1:3000')
  .split(',')
  .map((origin) => origin.trim());

app.use((req, res, next) => {
  const origin = req.headers.origin;
  if (origin && ALLOWED_ORIGINS.includes(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With, Accept');
  }
  if (req.method === 'OPTIONS') {
    return res.sendStatus(204);
  }
  next();
});

// Shared secret - the mission service (Java) validates tokens signed with
// this exact string. In a real system this would come from a secrets
// manager, never be hardcoded, and never be the same value in two
// unrelated services - here it's deliberately visible so the group can see
// EXACTLY what "the two services agree on a secret" means in practice.
const SECRET = process.env.JWT_SECRET || 'mission-control-shared-secret-key-32-bytes-minimum';

// A stub, not a real user store - two hardcoded accounts is enough to
// demonstrate "valid token in, protected data out" and "no token, or the
// wrong one, in -> rejected".
// CUSTOMER users can only use accounts whose owner_username matches their
// username: john owns ACC-1001 (ACTIVE), frank owns ACC-1008 (SUSPENDED, so
// cannot place orders) and nina has no account at all.
const USERS = {
  admin: { password: 'adminPassword', roles: ['ADMIN'] },
  alice: { password: 'mission123', roles: ['MISSION_OPERATOR', 'ADMIN'] },
  bob: { password: 'wrongpermissions', roles: ['GUEST'] },
  john: { password: 'customer123', roles: ['CUSTOMER'] },
  frank: { password: 'customer123', roles: ['CUSTOMER'] },
  nina: { password: 'customer123', roles: ['CUSTOMER'] },
};

const TOKEN_TTL_SECONDS = 3600;

// Returns a signed token for valid credentials, or null.
function issueToken(username, password) {
  const user = USERS[username];
  if (!user || user.password !== password) {
    return null;
  }
  return jwt.sign(
    { sub: username, roles: user.roles },
    SECRET,
    { algorithm: 'HS256', expiresIn: TOKEN_TTL_SECONDS }
  );
}

// JSON login used from the terminal: {"username": "...", "password": "..."} -> {"token": "..."}
app.post('/login', (req, res) => {
  const { username, password } = req.body || {};
  const token = issueToken(username, password);
  if (!token) {
    return res.status(401).json({ error: 'invalid username or password' });
  }
  res.json({ token });
});

// OAuth2 "password" grant (RFC 6749 section 4.3), the shape Swagger UI's
// Authorize dialog sends: grant_type=password&username=...&password=...
app.post('/token', (req, res) => {
  const { grant_type: grantType, username, password } = req.body || {};
  if (grantType !== 'password') {
    return res.status(400).json({ error: 'unsupported_grant_type' });
  }
  const token = issueToken(username, password);
  if (!token) {
    return res.status(400).json({ error: 'invalid_grant', error_description: 'invalid username or password' });
  }
  res.json({ access_token: token, token_type: 'Bearer', expires_in: TOKEN_TTL_SECONDS });
});

app.get('/health', (req, res) => res.json({ status: 'up' }));

const PORT = process.env.PORT || 4000;
app.listen(PORT, () => {
  console.log(`mission-auth-stub listening on http://localhost:${PORT}`);
  console.log(`Try: curl -X POST http://localhost:${PORT}/login -H "Content-Type: application/json" -d '{"username":"alice","password":"mission123"}'`);
});
