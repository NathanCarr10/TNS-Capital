import { HttpStatus, Injectable, Logger } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { ApiException } from '../common/api.exception';
import { hashPassword, verifyPassword } from '../users/password-hasher';
import { RefreshTokensService } from '../users/refresh-tokens.service';
import {
  AuthenticatedUser,
  RegisteredUser,
  UserRecord,
  UsernameTakenError,
  UsersService,
} from '../users/users.service';
import { AccessTokenPayload, REFRESH_TOKEN_TTL_SECONDS, TokenService } from './token.service';

export const INVALID_CREDENTIALS = 'invalid username or password';
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
   * Satisfies AC#4: Duplicate username returns 409
   */
  async register(username: string, password: string, holderName?: string): Promise<RegisteredUser> {
    // Checked before hashing, so a taken username doesn't cost an Argon2 hash
    if (await this.users.findByUsername(username)) {
      throw usernameTaken();
    }
    const passwordHash = await hashPassword(password);
    try {
      return await this.users.create(username, passwordHash, holderName);
    } catch (error) {
      if (error instanceof UsernameTakenError) {
        throw usernameTaken();
      }
      throw error;
    }
  }

  /**
   * Checks the credentials and issues an access token and a refresh token.
   * Each login starts a new session (refresh token family), so a user can be
   * signed in on several devices.
   *
   * Wrong username and wrong password get the same 401, so usernames cannot be probed.
   */
  async login(username: string, password: string): Promise<LoginResult> {
    const user = await this.users.findByUsername(username);
    if (!user || !(await verifyPassword(password, user.passwordHash))) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_CREDENTIALS);
    }

    const refreshToken = this.tokens.generateRefreshToken();
    await this.refreshTokens.create(user.id, refreshToken.hash, randomUUID(), REFRESH_TOKEN_TTL_SECONDS);
    return { accessToken: await this.tokens.issue(user), refreshToken: refreshToken.token };
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
    }
    if (result.status !== 'rotated') {
      throw invalidRefreshToken();
    }

    // Roles come from the database, so a role change applies at the next refresh
    const user = await this.users.findById(result.userId);
    if (!user) {
      throw invalidRefreshToken();
    }
    return { accessToken: await this.tokens.issue(user), refreshToken: next.token };
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
   * Turns a verified access token into the current user. Roles are re-read
   * from the database, so a role change applies before the token expires.
   *
   * @returns null if the user no longer exists
   */
  async validate(payload: AccessTokenPayload): Promise<AuthenticatedUser | null> {
    const user = await this.users.findByUsername(payload.sub);
    return user ? withoutPasswordHash(user) : null;
  }
}

function invalidRefreshToken(): ApiException {
  return new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_REFRESH_TOKEN);
}

function usernameTaken(): ApiException {
  return new ApiException(HttpStatus.CONFLICT, 'USR-409', 'User already exists');
}

// Satisfies AC#5: the password hash never leaves the service
function withoutPasswordHash({ id, username, roles, accountId }: UserRecord): AuthenticatedUser {
  return { id, username, roles, accountId };
}
