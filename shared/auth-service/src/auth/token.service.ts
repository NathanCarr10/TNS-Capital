import { HttpStatus, Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { createHash, randomBytes } from 'crypto';
import { ApiException } from '../common/api.exception';
import type { Role } from '../users/users.service';

export const JWT_ISSUER = 'urn:tns-capital:auth-stub';

/** Refresh tokens stop working 7 days after login */
export const REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60;

export const INVALID_ACCESS_TOKEN = 'Unauthorised or invalid token';

/** Claims of an access token (contract): exactly sub, roles, iss, iat, exp */
export interface AccessTokenPayload {
  sub: string;
  roles: Role[];
  iss: string;
  iat: number;
  exp: number;
}

export interface RefreshToken {
  /** Given to the client, never stored */
  token: string;
  /** Stored, so a database leak does not leak usable tokens */
  hash: string;
}

/**
 * Access tokens are JWTs: HS256 with the secret shared with the trading API,
 * valid for 1 hour (configured in AuthModule). Refresh tokens are opaque
 * random values; only their hash is stored.
 */
@Injectable()
export class TokenService {
  constructor(private readonly jwt: JwtService) {}

  issue(user: { username: string; roles: Role[] }): Promise<string> {
    return this.jwt.signAsync({ sub: user.username, roles: user.roles });
  }

  /**
   * Checks the signature, algorithm, issuer and expiry.
   *
   * @throws ApiException 401 AUTH-401 if any check fails
   */
  async verify(token: string): Promise<AccessTokenPayload> {
    let payload: Partial<AccessTokenPayload>;
    try {
      // Pinned here too: the algorithm is never taken from the token's header
      payload = await this.jwt.verifyAsync(token, { algorithms: ['HS256'], issuer: JWT_ISSUER });
    } catch {
      throw new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_ACCESS_TOKEN);
    }
    if (typeof payload.sub !== 'string' || !Array.isArray(payload.roles)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_ACCESS_TOKEN);
    }
    return payload as AccessTokenPayload;
  }

  /** Reads a token's claims WITHOUT checking it. Never use the result to grant access. */
  decode(token: string): AccessTokenPayload | null {
    const payload: unknown = this.jwt.decode(token);
    return payload !== null && typeof payload === 'object' ? (payload as AccessTokenPayload) : null;
  }

  generateRefreshToken(): RefreshToken {
    const token = randomBytes(32).toString('base64url');
    return { token, hash: this.hashRefreshToken(token) };
  }

  /**
   * SHA-256, not Argon2: the token is 256 random bits, so it cannot be
   * guessed, and an unsalted hash lets the database look it up directly.
   */
  hashRefreshToken(token: string): string {
    return createHash('sha256').update(token).digest('hex');
  }
}
