import { parseTrustProxy } from '../auth/rate-limit';

/**
 * Validates the environment at startup; the service refuses to start without
 * a usable JWT_SECRET, or with a weak ADMIN_PASSWORD or SUPPORT_PASSWORD.
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
  checkStaffPassword('ADMIN_PASSWORD', config.ADMIN_PASSWORD);
  checkStaffPassword('SUPPORT_PASSWORD', config.SUPPORT_PASSWORD);
  // One password for both would undo keeping the two roles apart
  if (config.ADMIN_PASSWORD && config.ADMIN_PASSWORD === config.SUPPORT_PASSWORD) {
    throw new Error('SUPPORT_PASSWORD must differ from ADMIN_PASSWORD.');
  }
  const rateLimit = config.AUTH_RATE_LIMIT;
  if (rateLimit !== undefined && !(Number.isInteger(Number(rateLimit)) && Number(rateLimit) > 0)) {
    throw new Error('AUTH_RATE_LIMIT must be a positive whole number.');
  }
  const metricsPort = config.METRICS_PORT;
  if (metricsPort !== undefined && metricsPort !== '' && !(Number.isInteger(Number(metricsPort)) && Number(metricsPort) > 0)) {
    throw new Error('METRICS_PORT must be a port number.');
  }
  const trustProxy = config.AUTH_TRUST_PROXY;
  if (trustProxy !== undefined) {
    if (typeof trustProxy !== 'string') {
      throw new TypeError('AUTH_TRUST_PROXY must be text: true, false, a number of hops or a list of addresses.');
    }
    parseTrustProxy(trustProxy);
  }
  return config;
}

/** A staff account's password, when set: at least 12 characters and not a published default. */
function checkStaffPassword(name: string, password: unknown): void {
  if (password === undefined || password === '') {
    return;
  }
  if (typeof password !== 'string' || password.length < 12) {
    throw new Error(`${name} must be at least 12 characters.`);
  }
  if (isPlaceholder(password)) {
    throw new Error(`${name} is a published placeholder or default. Choose your own.`);
  }
}
