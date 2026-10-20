import { HttpStatus, Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHash, timingSafeEqual } from 'node:crypto';
import { ApiException } from '../common/api.exception';
import { ACCESS_TOKEN_TTL_SECONDS, ServiceRole, TokenService } from './token.service';

export const INVALID_CLIENT = 'invalid client credentials';

/** Prefix of a service's token subject; a registered email never starts with it, so it never names a user */
export const SERVICE_SUBJECT_PREFIX = 'service:';

export interface ServiceTokenResult {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

interface ServiceClient {
  secretDigest: Buffer;
  roles: ServiceRole[];
}

/**
 * Backend services that log in with a client id and secret (OAuth 2.0 client
 * credentials) instead of an email and password, and get an access token
 * with a service role and no refresh token.
 *
 * The only client is the strategy service (STRATEGY_CLIENT_SECRET), which
 * places orders for traders who switched a strategy on while they are not
 * logged in. Its token's role STRATEGY is accepted by the trading API only for
 * accounts with an ACTIVE strategy (docs/DECISIONS.md #16). Without the
 * secret the client does not exist and every request is refused.
 */
@Injectable()
export class ServiceClientsService {
  private readonly logger = new Logger(ServiceClientsService.name);
  private readonly clients = new Map<string, ServiceClient>();

  constructor(
    config: ConfigService,
    private readonly tokens: TokenService,
  ) {
    const strategySecret = config.get<string>('STRATEGY_CLIENT_SECRET');
    if (strategySecret) {
      this.clients.set('strategy-service', { secretDigest: digest(strategySecret), roles: ['STRATEGY'] });
    }
  }

  /**
   * Checks the client's secret and issues a 15-minute access token for it.
   * An unknown client and a wrong secret get the same 401.
   */
  async issueToken(clientId: string, clientSecret: string, clientIp = 'unknown'): Promise<ServiceTokenResult> {
    const client = this.clients.get(clientId);
    // Digests have the same length, so timingSafeEqual compares them in constant time
    if (!client || !timingSafeEqual(client.secretDigest, digest(clientSecret))) {
      this.logger.warn(`Service login failed: clientId=${JSON.stringify(clientId)} ip=${clientIp}`);
      throw new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_CLIENT);
    }
    this.logger.log(`Service login succeeded: clientId=${JSON.stringify(clientId)} ip=${clientIp}`);
    const accessToken = await this.tokens.issue(SERVICE_SUBJECT_PREFIX + clientId, client.roles);
    return { accessToken, tokenType: 'Bearer', expiresIn: ACCESS_TOKEN_TTL_SECONDS };
  }
}

function digest(value: string): Buffer {
  return createHash('sha256').update(value).digest();
}
