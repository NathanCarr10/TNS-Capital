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
  checkJwtSecret(config.JWT_SECRET);
  checkAdminPassword(config.ADMIN_PASSWORD);
  checkStrategyClientSecret(config.STRATEGY_CLIENT_SECRET);
  checkRateLimit(config.AUTH_RATE_LIMIT);
  checkTrustProxy(config.AUTH_TRUST_PROXY);
  return config;
}

function checkJwtSecret(secret: unknown): void {
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
}

function checkAdminPassword(adminPassword: unknown): void {
  if (adminPassword === undefined || adminPassword === '') {
    return;
  }
  if (typeof adminPassword !== 'string' || adminPassword.length < 12) {
    throw new Error('ADMIN_PASSWORD must be at least 12 characters.');
  }
  if (isPlaceholder(adminPassword)) {
    throw new Error('ADMIN_PASSWORD is a published placeholder or default. Choose your own.');
  }
}

/** Optional: without it the strategy service cannot log in (POST /auth/token refuses it) */
function checkStrategyClientSecret(strategySecret: unknown): void {
  if (strategySecret === undefined || strategySecret === '') {
    return;
  }
  if (typeof strategySecret !== 'string' || strategySecret.length < 32) {
    throw new Error('STRATEGY_CLIENT_SECRET must be at least 32 characters. Generate one with: openssl rand -hex 32');
  }
  if (isPlaceholder(strategySecret)) {
    throw new Error('STRATEGY_CLIENT_SECRET is the placeholder from .env.example. Generate one with: openssl rand -hex 32');
  }
}

function checkRateLimit(rateLimit: unknown): void {
  if (rateLimit !== undefined && !(Number.isInteger(Number(rateLimit)) && Number(rateLimit) > 0)) {
    throw new Error('AUTH_RATE_LIMIT must be a positive whole number.');
  }
}

function checkTrustProxy(trustProxy: unknown): void {
  if (trustProxy === undefined) {
    return;
  }
  if (typeof trustProxy !== 'string') {
    throw new TypeError('AUTH_TRUST_PROXY must be text: true, false, a number of hops or a list of addresses.');
  }
  parseTrustProxy(trustProxy);
}
