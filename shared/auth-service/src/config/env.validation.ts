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
  checkJwtSecret(config.JWT_SECRET);
  checkStaffPasswords(config);
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

function checkStaffPasswords(config: Record<string, unknown>): void {
  checkStaffPassword('ADMIN_PASSWORD', config.ADMIN_PASSWORD);
  checkStaffPassword('SUPPORT_PASSWORD', config.SUPPORT_PASSWORD);
  // One password for both would undo keeping the two roles apart
  if (config.ADMIN_PASSWORD && config.ADMIN_PASSWORD === config.SUPPORT_PASSWORD) {
    throw new Error('SUPPORT_PASSWORD must differ from ADMIN_PASSWORD.');
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
