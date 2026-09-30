const express = require('express');
const jwt = require('jsonwebtoken');

const app = express();
app.use(express.json());

// Shared secret - the mission service (Java) validates tokens signed with
// this exact value. Both services read it from the JWT_SECRET environment
// variable (set in the project's .env), so it is never committed.
const SECRET = process.env.JWT_SECRET;
if (!SECRET) {
  console.error('JWT_SECRET is not set. Add it to .env or the environment.');
  process.exit(1);
}

// A stub, not a real user store - two hardcoded accounts is enough to
// demonstrate "valid token in, protected data out" and "no token, or the
// wrong one, in -> rejected".
const USERS = {
  alice: { password: 'mission123', roles: ['MISSION_OPERATOR'] },
  bob: { password: 'wrongpermissions', roles: ['GUEST'] },
};

app.post('/login', (req, res) => {
  const { username, password } = req.body || {};
  const user = USERS[username];
  if (!user || user.password !== password) {
    return res.status(401).json({ error: 'invalid username or password' });
  }
  const token = jwt.sign(
    { sub: username, roles: user.roles },
    SECRET,
    { algorithm: 'HS256', expiresIn: '1h' }
  );
  res.json({ token });
});

app.get('/health', (req, res) => res.json({ status: 'up' }));

const PORT = process.env.PORT || 4000;
app.listen(PORT, () => {
  console.log(`mission-auth-stub listening on http://localhost:${PORT}`);
  console.log(`Try: curl -X POST http://localhost:${PORT}/login -H "Content-Type: application/json" -d '{"username":"alice","password":"mission123"}'`);
});
