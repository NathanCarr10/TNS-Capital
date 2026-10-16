/** Credential requests (register, login, refresh) allowed per client IP per minute */
export const DEFAULT_AUTH_RATE_LIMIT = 10;

/**
 * Which proxies may tell the service the client's IP (Express "trust proxy").
 *
 * Browsers reach the service through the frontend's nginx, which adds the
 * client's address to X-Forwarded-For. Without trusting it, every request
 * would come from nginx's address, so all users would share one rate limit
 * and the login log would show nginx instead of the client.
 *
 * The default trusts the loopback and private networks, which covers nginx on
 * the Docker network. A request from anywhere else is taken at its own
 * address, so X-Forwarded-For cannot be forged to dodge the limit. Behind
 * other proxies (sprint 11: CloudFront and a load balancer), set
 * AUTH_TRUST_PROXY to their addresses or to the number of proxy hops.
 */
export const DEFAULT_TRUST_PROXY = 'loopback, uniquelocal';

/**
 * AUTH_TRUST_PROXY as Express expects it: "false" or "true", a number of
 * hops, or a comma-separated list of addresses, subnets or the names
 * loopback, linklocal and uniquelocal.
 *
 * @throws Error if the value is blank or a negative or fractional number
 */
export function parseTrustProxy(value: string | undefined): boolean | number | string {
  if (value === undefined) {
    return DEFAULT_TRUST_PROXY;
  }
  const trimmed = value.trim();
  if (trimmed === 'false' || trimmed === 'true') {
    return trimmed === 'true';
  }
  // A plain number (an IPv4 address has three dots) is a number of hops
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) {
    const hops = Number(trimmed);
    if (!Number.isInteger(hops) || hops < 0) {
      throw new Error('AUTH_TRUST_PROXY must be a whole number of hops, true, false or a list of addresses.');
    }
    return hops;
  }
  if (trimmed === '' || trimmed.split(',').some((entry) => entry.trim() === '')) {
    throw new Error('AUTH_TRUST_PROXY must not be blank or contain an empty entry.');
  }
  return trimmed;
}
