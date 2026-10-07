/**
 * Validates the environment at startup; the service refuses to start without
 * a usable JWT_SECRET.
 *
 * The trading API (Java) validates tokens signed with this exact value. Both
 * services read it from JWT_SECRET (set in the project's .env), so it is
 * never committed.
 */
export function validateEnv(config: Record<string, unknown>): Record<string, unknown> {
  const secret = config.JWT_SECRET;
  if (typeof secret !== 'string' || secret.length === 0) {
    throw new Error('JWT_SECRET is not set. Add it to .env or the environment.');
  }
  if (secret.length < 32) {
    throw new Error('JWT_SECRET must be at least 32 bytes (256 bits) for HS256 security.');
  }
  return config;
}
