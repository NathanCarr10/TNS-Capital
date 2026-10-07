const argon2 = require('argon2');

/**
 * Argon2id configuration
 *
 * DECISION RATIONALE (see docs/SECURITY_GUIDE.md):
 * - Algorithm: Argon2id (memory-hard, resistant to GPU/ASIC attacks)
 * - Memory: 65,540 KiB (~64 MiB) — strong against brute force
 * - Time: 3 iterations — ~50-100ms per hash on typical hardware
 * - Parallelism: 4 threads — utilizes multi-core without being excessive
 *
 * This balance provides:
 * ✓ Strong security (hard for attackers to crack)
 * ✓ Reasonable performance (not too slow for legitimate users)
 * ✓ Defensible parameters (industry standard, tuned for 2025 hardware)
 */
const ARGON2_OPTIONS = {
  type: argon2.argon2id,  // Memory-hard, resistant to GPU attacks
  memoryCost: 65540,       // 64 MiB
  timeCost: 3,             // 3 iterations (~50-100ms)
  parallelism: 4,          // 4 threads
};

/**
 * Hash a plaintext password using Argon2id
 * Returns full hash string including algorithm, parameters, salt, and hash
 *
 * @param {string} password - Plaintext password to hash
 * @returns {Promise<string>} Full Argon2id hash string
 * @throws {Error} If password is invalid or hashing fails
 *
 * Satisfies AC#1: Plaintext never stored, only Argon2id hash
 * Satisfies AC#3: Hash string includes algorithm, parameters, and salt
 */
async function hashPassword(password) {
  if (!password || typeof password !== 'string' || password.length < 8) {
    throw new Error('Password must be at least 8 characters');
  }
  return await argon2.hash(password, ARGON2_OPTIONS);
}

/**
 * Verify a plaintext password against a stored Argon2id hash
 *
 * @param {string} password - Plaintext password to verify
 * @param {string} hash - Stored Argon2id hash
 * @returns {Promise<boolean>} True if password matches, false otherwise
 *
 * Satisfies AC#6: Tests verify that stored hash differs from input
 *                  and that verification succeeds with correct password
 */
async function verifyPassword(password, hash) {
  try {
    return await argon2.verify(hash, password);
  } catch (error) {
    // Invalid hash format returns false, not thrown error
    return false;
  }
}

module.exports = {
  hashPassword,
  verifyPassword,
  ARGON2_OPTIONS, // Export for documentation and testing
};
