const express = require('express');
const jwt = require('jsonwebtoken');
require('dotenv').config();

const { registerUser, authenticateUser } = require('./src/userService');

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

/**
 * Creates a platform-compliant error response envelope
 * @param {string} errorCode - Error code (e.g., "AUTH-401", "VAL-422", "SYS-500")
 * @param {string} message - Human-readable error message (no stack traces)
 * @returns {Object} Error response with errorCode, message, and ISO 8601 timestamp
 */
function errorResponse(errorCode, message) {
  return {
    errorCode,
    message,
    timestamp: new Date().toISOString(),
  };
}

/**
 * POST /register - Register a new user
 *
 * Satisfies AC#1: Passwords hashed with Argon2id, plaintext never stored
 * Satisfies AC#4: Duplicate username returns 409 error
 * Satisfies AC#5: Response never includes password or hash
 */
app.post('/register', async (req, res) => {
  try {
    const { username, password } = req.body || {};

    // Validate inputs (AC#5 - don't log password)
    if (!username || !password) {
      return res.status(422).json(errorResponse('VAL-422', 'missing username or password'));
    }

    // Register user (AC#1 - hashes password)
    const user = await registerUser(username, password);

    // Return user info WITHOUT password (AC#5)
    return res.status(201).json({
      id: user.id,
      username: user.username,
      createdAt: user.created_at,
    });
  } catch (error) {
    // Handle duplicate username (AC#4)
    if (error.code === 'CONFLICT') {
      return res.status(409).json(errorResponse('USR-409', 'User already exists'));
    }

    // Validation errors
    if (error.message.includes('must be')) {
      return res.status(422).json(errorResponse('VAL-422', error.message));
    }

    // Generic server error (AC#5 - never expose internals)
    console.error('Registration error:', error);
    return res.status(500).json(errorResponse('SYS-500', 'Internal server error'));
  }
});

/**
 * POST /login - Authenticate user and issue JWT
 *
 * Satisfies AC#1: Verifies password against Argon2id hash
 * Satisfies AC#5: Never logs password, response only contains token
 */
app.post('/login', async (req, res) => {
  try {
    const { username, password } = req.body || {};

    if (!username || !password) {
      return res.status(422).json(errorResponse('VAL-422', 'missing username or password'));
    }

    // Authenticate against database (AC#1, AC#6)
    const user = await authenticateUser(username, password);

    if (!user) {
      // Don't reveal whether user exists or password is wrong (security best practice)
      return res.status(401).json(errorResponse('AUTH-401', 'invalid username or password'));
    }

    // Issue JWT token
    // TODO: Assign roles based on user attributes (for now, hardcoded pending auth model expansion)
    const token = jwt.sign(
      { sub: username, roles: ['USER'] },
      SECRET,
      { algorithm: 'HS256', expiresIn: '1h' },
    );

    return res.json({ token });
  } catch (error) {
    console.error('Login error:', error);
    return res.status(500).json(errorResponse('SYS-500', 'Internal server error'));
  }
});

app.get('/health', (req, res) => res.json({ status: 'up' }));

const PORT = process.env.PORT || 4000;
app.listen(PORT, () => {
  console.log(`Auth service listening on http://localhost:${PORT}`);
  console.log(`Try: curl -X POST http://localhost:${PORT}/login -H "Content-Type: application/json" -d '{"username":"alice","password":"alice123"}'`);
});
