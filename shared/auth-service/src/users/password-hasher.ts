import * as argon2 from 'argon2';

/**
 * Argon2id configuration
 *
 * DECISION RATIONALE (see docs/SECURITY_GUIDE.md):
 * - Algorithm: Argon2id (memory-hard, resistant to GPU/ASIC attacks)
 * - Memory: 65,540 KiB (~64 MiB) — strong against brute force
 * - Time: 3 iterations — ~50-100ms per hash on typical hardware
 * - Parallelism: 4 threads — utilizes multi-core without being excessive
 */
export const ARGON2_OPTIONS = {
  type: argon2.argon2id, // Memory-hard, resistant to GPU attacks
  memoryCost: 65540, // 64 MiB
  timeCost: 3, // 3 iterations (~50-100ms)
  parallelism: 4, // 4 threads
};

/**
 * Hash a plaintext password using Argon2id.
 * Returns the full hash string including algorithm, parameters, salt, and hash.
 *
 * Satisfies AC#1: Plaintext never stored, only Argon2id hash
 * Satisfies AC#3: Hash string includes algorithm, parameters, and salt
 */
export async function hashPassword(password: string): Promise<string> {
  if (!password || typeof password !== 'string' || password.length < 8) {
    throw new Error('Password must be at least 8 characters');
  }
  return argon2.hash(password, ARGON2_OPTIONS);
}

/**
 * Verify a plaintext password against a stored Argon2id hash.
 * An invalid hash format returns false rather than throwing.
 *
 * Satisfies AC#6: verification succeeds only with the correct password
 */
export async function verifyPassword(password: string, hash: string): Promise<boolean> {
  try {
    return await argon2.verify(hash, password);
  } catch {
    return false;
  }
}
