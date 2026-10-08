// Access tokens are short-lived: the trading API verifies them locally and
// cannot revoke one, so this is the longest a stolen access token works.
export const ACCESS_TOKEN_TTL_SECONDS = 15 * 60;

// Refresh tokens rotate on every use, so this only bounds an idle session:
// a user who has not refreshed for 7 days has to log in again.
export const REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60;

export const AUTH_CONFIG = Symbol("AUTH_CONFIG");

export interface AuthConfig {
  // Shared with the trading API (its jwt.shared-secret), which verifies access tokens.
  accessSecret: string;
  // Known only to this service, so the trading API can never accept a refresh token.
  refreshSecret: string;
  issuer: string;
}

// HS256 keys must be at least 256 bits; the trading API's Nimbus decoder rejects shorter ones.
const MIN_SECRET_LENGTH = 32;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AuthConfig {
  const accessSecret = requireSecret(env, "JWT_SECRET");
  const refreshSecret = requireSecret(env, "JWT_REFRESH_SECRET");
  if (accessSecret === refreshSecret) {
    throw new Error("JWT_REFRESH_SECRET must be different from JWT_SECRET");
  }
  return { accessSecret, refreshSecret, issuer: env.JWT_ISSUER || "tns-capital-auth" };
}

function requireSecret(env: NodeJS.ProcessEnv, name: string): string {
  const value = env[name];
  if (!value) {
    throw new Error(`${name} is not set. Add it to .env or the environment.`);
  }
  if (value.length < MIN_SECRET_LENGTH) {
    throw new Error(`${name} must be at least ${MIN_SECRET_LENGTH} characters`);
  }
  return value;
}
