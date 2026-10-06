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

/**
 * Creates a platform-compliant error response envelope matching Java ErrorResponse
 * @param {string} errorCode - Error code (e.g., "AUTH-401", "VAL-422", "SYS-500")
 * @param {string} message - Human-readable error message (no stack traces or internal details)
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
 * Validates login request body against contract schema
 * Contract schema requires:
 * - username: string, minLength 1, maxLength 100, required
 * - password: string, minLength 1, maxLength 256, required
 * - additionalProperties: false
 * @param {Object} body - Request body
 * @returns {Object|null} Validation error object or null if valid
 */
function validateLoginRequest(body) {
  if (!body) {
    return { code: 'VAL-422', message: 'missing username or password' };
  }

  const { username, password } = body;

  // Check for required fields (undefined/null/missing)
  if (username === undefined || username === null) {
    return { code: 'VAL-422', message: 'missing username or password' };
  }
  if (password === undefined || password === null) {
    return { code: 'VAL-422', message: 'missing username or password' };
  }

  // Check types
  if (typeof username !== 'string') {
    return { code: 'VAL-422', message: 'username must be a string' };
  }
  if (typeof password !== 'string') {
    return { code: 'VAL-422', message: 'password must be a string' };
  }

  // Check length constraints (minLength 1, maxLength enforced by contract)
  if (username.length < 1 || username.length > 100) {
    return { code: 'VAL-422', message: 'username must be between 1 and 100 characters' };
  }
  if (password.length < 1 || password.length > 256) {
    return { code: 'VAL-422', message: 'password must be between 1 and 256 characters' };
  }

  // Check for additionalProperties - reject unknown fields per contract schema
  const allowedKeys = new Set(['username', 'password']);
  for (const key of Object.keys(body)) {
    if (!allowedKeys.has(key)) {
      return { code: 'VAL-422', message: `Unexpected property: ${key}` };
    }
  }

  return null; // Valid
}

// POST /login - Authenticate user and return JWT token
app.post('/login', (req, res, next) => {
  try {
    // Validate request body against contract schema
    const validationError = validateLoginRequest(req.body);
    if (validationError) {
      return res.status(400).json(errorResponse(validationError.code, validationError.message));
    }

    const { username, password } = req.body;
    const user = USERS[username];

    // Invalid credentials - 401 response in platform envelope
    if (!user || user.password !== password) {
      return res.status(401).json(errorResponse('AUTH-401', 'invalid username or password'));
    }

    // Valid credentials - issue JWT token
    const token = jwt.sign(
      { sub: username, roles: user.roles },
      SECRET,
      { algorithm: 'HS256', expiresIn: '1h' }
    );
    res.json({ token });
  } catch (error) {
    next(error);
  }
});

// POST /token - OAuth2 "password" grant (RFC 6749 section 4.3), the shape
// Swagger UI's Authorize dialog sends: grant_type=password&username=...&password=...
// Errors use the OAuth2 format ({ error }) because that is what Swagger UI reads.
const TOKEN_TTL_SECONDS = 3600;

app.post('/token', (req, res, next) => {
  try {
    const { grant_type: grantType, username, password } = req.body || {};
    if (grantType !== 'password') {
      return res.status(400).json({ error: 'unsupported_grant_type' });
    }
    const user = USERS[username];
    if (!user || user.password !== password) {
      return res.status(400).json({ error: 'invalid_grant', error_description: 'invalid username or password' });
    }
    const token = jwt.sign(
      { sub: username, roles: user.roles },
      SECRET,
      { algorithm: 'HS256', expiresIn: TOKEN_TTL_SECONDS }
    );
    res.json({ access_token: token, token_type: 'Bearer', expires_in: TOKEN_TTL_SECONDS });
  } catch (error) {
    next(error);
  }
});

// GET /health - Service health check
app.get('/health', (req, res, next) => {
  try {
    res.json({ status: 'up' });
  } catch (error) {
    next(error);
  }
});

// 405 handler - /health only allows GET
app.post('/health', (req, res) => {
  res.status(405).json(
    errorResponse('REQ-405', 'HTTP method POST is not supported on this endpoint')
  );
});
app.put('/health', (req, res) => {
  res.status(405).json(
    errorResponse('REQ-405', 'HTTP method PUT is not supported on this endpoint')
  );
});
app.delete('/health', (req, res) => {
  res.status(405).json(
    errorResponse('REQ-405', 'HTTP method DELETE is not supported on this endpoint')
  );
});
app.patch('/health', (req, res) => {
  res.status(405).json(
    errorResponse('REQ-405', 'HTTP method PATCH is not supported on this endpoint')
  );
});

// 405 handler - /login only allows POST
app.get('/login', (req, res) => {
  res.status(405).json(
    errorResponse('REQ-405', 'HTTP method GET is not supported on this endpoint')
  );
});
app.put('/login', (req, res) => {
  res.status(405).json(
    errorResponse('REQ-405', 'HTTP method PUT is not supported on this endpoint')
  );
});
app.delete('/login', (req, res) => {
  res.status(405).json(
    errorResponse('REQ-405', 'HTTP method DELETE is not supported on this endpoint')
  );
});
app.patch('/login', (req, res) => {
  res.status(405).json(
    errorResponse('REQ-405', 'HTTP method PATCH is not supported on this endpoint')
  );
});

// 404 handler - Unknown routes (must be after all defined routes)
app.use((req, res) => {
  res.status(404).json(
    errorResponse('NOT-404', `Resource not found: ${req.method} ${req.path}`)
  );
});

/**
 * Global error middleware - handles all unhandled exceptions
 * Must be registered last to catch errors from all routes and middleware
 * Never exposes stack traces or internal details to clients
 * Logs full details server-side for debugging
 */
app.use((err, req, res, next) => {
  // Specifically handle JSON parsing errors from express.json()
  if (err instanceof SyntaxError && 'body' in err) {
    return res.status(400).json(
      errorResponse('VAL-422', 'Request body is missing or malformed')
    );
  }

  // Log full details server-side for debugging (but never expose to client)
  console.error('Unhandled exception:', err);

  // Return generic error response without exposing any internal details
  res.status(500).json(
    errorResponse(
      'SYS-500',
      'An unexpected error occurred. Please contact support with error timestamp if problem persists.'
    )
  );
});

// Only listen if this is the main module (being run directly, not imported for testing)
if (require.main === module) {
  const PORT = process.env.PORT || 4000;
  const server = app.listen(PORT, () => {
    console.log(`mission-auth-stub listening on http://localhost:${PORT}`);
    console.log(`Try: curl -X POST http://localhost:${PORT}/login -H "Content-Type: application/json" -d '{"username":"alice","password":"mission123"}'`);
  });
}

module.exports = app; // Export for testing with Jest/Supertest
