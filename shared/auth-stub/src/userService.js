const pool = require('./db');
const { hashPassword, verifyPassword } = require('./passwordHasher');

/**
 * Maps usernames to their authorized roles
 * Per contract: x-internal-notes User Store
 *
 * @param {string} username - Username
 * @returns {string[]} Array of role strings
 */
function getRolesForUser(username) {
  const roleMap = {
    alice: ['MISSION_OPERATOR', 'ADMIN'],
    bob: ['GUEST'],
  };
  return roleMap[username] || ['USER']; // Default to USER for unknown users
}

/**
 * Register a new user with hashed password
 *
 * @param {string} username - Username to register
 * @param {string} password - Plaintext password
 * @returns {Promise<{id, username}>} Newly created user (password excluded)
 * @throws {Error} If username already exists
 *
 * Satisfies AC#4: Duplicate username throws error (409), doesn't overwrite
 * Satisfies AC#1: Plaintext never stored in database
 */
async function registerUser(username, password) {
  // Validate inputs
  if (!username || username.length < 3 || username.length > 100) {
    throw new Error('Username must be 3-100 characters');
  }
  if (!password || password.length < 8 || password.length > 256) {
    throw new Error('Password must be 8-256 characters');
  }

  // Check if username already exists
  const existing = await pool.query('SELECT id FROM users WHERE username = $1', [username]);
  if (existing.rows.length > 0) {
    const err = new Error('Username already exists');
    err.code = 'CONFLICT'; // Will map to 409 in controller
    throw err;
  }

  // Hash password (never stored plaintext)
  const passwordHash = await hashPassword(password);

  // Insert user
  const result = await pool.query(
    'INSERT INTO users (username, password_hash, created_at, updated_at) VALUES ($1, $2, NOW(), NOW()) RETURNING id, username, created_at',
    [username, passwordHash],
  );

  return result.rows[0];
}

/**
 * Authenticate user by username and password
 *
 * @param {string} username - Username
 * @param {string} password - Plaintext password
 * @returns {Promise<{id, username, roles}|null>} User object with roles if authenticated, null otherwise
 *
 * Satisfies AC#1: Compares plaintext input to stored hash only
 * Satisfies AC#6: Verifies password matches stored hash
 */
async function authenticateUser(username, password) {
  const result = await pool.query('SELECT id, username, password_hash FROM users WHERE username = $1', [username]);

  if (result.rows.length === 0) {
    return null; // User not found
  }

  const user = result.rows[0];
  const isValid = await verifyPassword(password, user.password_hash);

  if (!isValid) {
    return null; // Password mismatch
  }

  // Return user WITHOUT password_hash (AC#5) and WITH roles
  return {
    id: user.id,
    username: user.username,
    roles: getRolesForUser(user.username),
  };
}

module.exports = {
  registerUser,
  authenticateUser,
};
