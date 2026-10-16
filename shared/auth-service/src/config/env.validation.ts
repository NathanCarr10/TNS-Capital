import { parseTrustProxy } from '../auth/rate-limit';

/**
 * Validates the environment at startup; the service refuses to start without
 * a usable JWT_SECRET, or with a weak ADMIN_PASSWORD.
 *
 * The trading API (Java) validates tokens signed with this exact value. Both
 * services read it from JWT_SECRET (set in the project's .env), so it is
 * never committed.
 */

/** Values copied unedited from .env.example or the docs: published, so never secret. */
export function isPlaceholder(value: string): boolean {
  return /^change-?me/i.test(value) || value === 'adminPassword';
}

export function validateEnv(config: Record<string, unknown>): Record<string, unknown> {
  const secret = config.JWT_SECRET;
  if (typeof secret !== 'string' || secret.length === 0) {
    throw new Error('JWT_SECRET is not set. Add it to .env or the environment.');
  }
  if (secret.length < 32) {
    throw new Error('JWT_SECRET must be at least 32 bytes (256 bits) for HS256 security.');
  }
  // Anyone who knows the signing key can forge an ADMIN token (OWASP A02-5)
  if (isPlaceholder(secret)) {
    throw new Error('JWT_SECRET is the placeholder from .env.example. Generate one with: openssl rand -hex 32');
  }
  const adminPassword = config.ADMIN_PASSWORD;
  if (adminPassword !== undefined && adminPassword !== '') {
    if (typeof adminPassword !== 'string' || adminPassword.length < 12) {
      throw new Error('ADMIN_PASSWORD must be at least 12 characters.');
    }
    if (isPlaceholder(adminPassword)) {
      throw new Error('ADMIN_PASSWORD is a published placeholder or default. Choose your own.');
    }
  }
  const rateLimit = config.AUTH_RATE_LIMIT;
  if (rateLimit !== undefined && !(Number.isInteger(Number(rateLimit)) && Number(rateLimit) > 0)) {
    throw new Error('AUTH_RATE_LIMIT must be a positive whole number.');
  }
  const trustProxy = config.AUTH_TRUST_PROXY;
  if (trustProxy !== undefined) {
    if (typeof trustProxy !== 'string') {
      throw new Error('AUTH_TRUST_PROXY must be text: true, false, a number of hops or a list of addresses.');
    }
    parseTrustProxy(trustProxy);
  }
  return config;
}
