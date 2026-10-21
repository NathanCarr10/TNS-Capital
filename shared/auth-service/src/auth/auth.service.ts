import { HttpStatus, Injectable, Logger } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { ApiException } from '../common/api.exception';
import { metrics } from '../common/metrics';
import { hashPassword, verifyPassword } from '../users/password-hasher';
import { RefreshTokensService } from '../users/refresh-tokens.service';
import {
  AuthenticatedUser,
  RegisteredUser,
  EmailTakenError,
  normaliseEmail,
  UserRecord,
  UsersService,
} from '../users/users.service';
import { AccessTokenPayload, INVALID_ACCESS_TOKEN, REFRESH_TOKEN_TTL_SECONDS, TokenService } from './token.service';

export const INVALID_CREDENTIALS = 'invalid email or password';
export const INVALID_REFRESH_TOKEN = 'invalid or expired refresh token';

export interface LoginResult {
  accessToken: string;
  refreshToken: string;
}

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    private readonly users: UsersService,
    private readonly tokens: TokenService,
    private readonly refreshTokens: RefreshTokensService,
  ) {}

  /**
   * Registers a user and opens their trading account.
   *
   * Satisfies AC#1: Plaintext never stored, only the Argon2id hash
   * Satisfies AC#4: Duplicate email returns 409
   */
  async register(email: string, password: string, holderName?: string): Promise<RegisteredUser> {
    const normalisedEmail = normaliseEmail(email);
    // Checked before hashing, so a taken email doesn't cost an Argon2 hash
    if (await this.users.findByEmail(normalisedEmail)) {
      throw emailTaken();
    }
    const passwordHash = await hashPassword(password);
    try {
      return await this.users.create(normalisedEmail, passwordHash, holderName);
    } catch (error) {
      if (error instanceof EmailTakenError) {
        throw emailTaken();
      }
      throw error;
    }
  }

  /**
   * Checks an email address and password.
   *
   * @returns the user, or null if the email is unknown or the password is
   *          wrong (callers must not reveal which)
   */
  async validate(email: string, password: string): Promise<UserRecord | null> {
    const user = await this.users.findByEmail(normaliseEmail(email));
    if (!user || !(await verifyPassword(password, user.passwordHash))) {
      return null;
    }
    return user;
  }

  /**
   * Checks the credentials and issues an access token and a refresh token.
   * Each login starts a new session (refresh token family), so a user can be
   * signed in on several devices.
   *
   * Wrong email and wrong password get the same 401, so accounts cannot be probed.
   */
  async login(email: string, password: string, clientIp = 'unknown'): Promise<LoginResult> {
    const user = await this.validate(email, password);
    // Every attempt is logged, so password guessing leaves a trace (OWASP A09-2). Never the
    // password or tokens; the email is quoted so a crafted value cannot forge log lines.
    if (!user) {
      this.logger.warn(`Login failed: email=${JSON.stringify(email)} ip=${clientIp}`);
      metrics.loginFailed();
      throw new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_CREDENTIALS);
    }
    this.logger.log(`Login succeeded: email=${JSON.stringify(user.email)} id=${user.id} ip=${clientIp}`);
    metrics.loginSucceeded();

    const refreshToken = this.tokens.generateRefreshToken();
    await this.refreshTokens.create(user.id, refreshToken.hash, randomUUID(), REFRESH_TOKEN_TTL_SECONDS);
    return { accessToken: await this.tokens.issue(user.email, user.roles), refreshToken: refreshToken.token };
  }

  /**
   * Rotates: the presented refresh token is spent and a new access token and
   * refresh token are issued in the same session.
   *
   * A spent token presented again means it was copied. The service cannot
   * tell the thief from the user, so the whole session is revoked and both
   * have to log in again.
   */
  async refresh(refreshToken: string): Promise<LoginResult> {
    const next = this.tokens.generateRefreshToken();
    const result = await this.refreshTokens.rotate(
      this.tokens.hashRefreshToken(refreshToken),
      next.hash,
      REFRESH_TOKEN_TTL_SECONDS,
    );
    if (result.status === 'reused') {
      this.logger.warn(`Refresh token reuse detected for user id=${result.userId}; session revoked`);
      metrics.refreshTokenReused();
    }
    if (result.status !== 'rotated') {
      throw invalidRefreshToken();
    }

    // Roles come from the database, so a role change applies at the next refresh
    const user = await this.users.findById(result.userId);
    if (!user) {
      throw invalidRefreshToken();
    }
    return { accessToken: await this.tokens.issue(user.email, user.roles), refreshToken: next.token };
  }

  /**
   * Revokes the session the refresh token belongs to. Access tokens already
   * issued stay valid until they expire (at most 15 minutes).
   */
  async logout(refreshToken: string): Promise<{ loggedOut: true }> {
    if (!(await this.refreshTokens.revokeFamily(this.tokens.hashRefreshToken(refreshToken)))) {
      throw invalidRefreshToken();
    }
    return { loggedOut: true };
  }

  /**
   * The current user record for a verified access token, for GET /auth/me.
   * Roles and account are read from the database, so a role change shows here
   * before the token expires.
   *
   * @throws ApiException 401 AUTH-401 if the user no longer exists
   */
  async me(claims: AccessTokenPayload): Promise<AuthenticatedUser> {
    const user = await this.users.findByEmail(claims.sub);
    if (!user) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_ACCESS_TOKEN);
    }
    return withoutPasswordHash(user);
  }
}

function invalidRefreshToken(): ApiException {
  return new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_REFRESH_TOKEN);
}

function emailTaken(): ApiException {
  return new ApiException(HttpStatus.CONFLICT, 'USR-409', 'User already exists');
}

// Satisfies AC#5: the password hash never leaves the service
function withoutPasswordHash({ id, email, roles, accountId }: UserRecord): AuthenticatedUser {
  return { id, email, roles, accountId };
}
