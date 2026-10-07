import { HttpStatus, Injectable } from '@nestjs/common';
import { ApiException } from '../common/api.exception';
import { hashPassword, verifyPassword } from '../users/password-hasher';
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
  constructor(
    private readonly users: UsersService,
    private readonly tokens: TokenService,
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
   * The new refresh token replaces the user's previous one.
   *
   * Wrong username and wrong password get the same 401, so usernames cannot be probed.
   */
  async login(username: string, password: string): Promise<LoginResult> {
    const user = await this.users.findByUsername(username);
    if (!user || !(await verifyPassword(password, user.passwordHash))) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_CREDENTIALS);
    }

    const accessToken = await this.tokens.issue(user);
    const refreshToken = this.tokens.generateRefreshToken();
    await this.users.setRefreshToken(user.id, refreshToken.hash, REFRESH_TOKEN_TTL_SECONDS);
    return { accessToken, refreshToken: refreshToken.token };
  }

  /** Issues a new access token. The refresh token itself stays the same until it expires. */
  async refresh(refreshToken: string): Promise<{ accessToken: string }> {
    const user = await this.findByRefreshToken(refreshToken);
    return { accessToken: await this.tokens.issue(user) };
  }

  /**
   * Revokes the refresh token so it can never be used again. Access tokens
   * already issued stay valid until they expire (at most 1 hour).
   */
  async logout(refreshToken: string): Promise<{ loggedOut: true }> {
    const user = await this.findByRefreshToken(refreshToken);
    await this.users.clearRefreshToken(user.id);
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

  private async findByRefreshToken(refreshToken: string): Promise<UserRecord> {
    const user = await this.users.findByRefreshTokenHash(this.tokens.hashRefreshToken(refreshToken));
    if (!user) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_REFRESH_TOKEN);
    }
    return user;
  }
}

function usernameTaken(): ApiException {
  return new ApiException(HttpStatus.CONFLICT, 'USR-409', 'User already exists');
}

// Satisfies AC#5: the password hash never leaves the service
function withoutPasswordHash({ id, username, roles, accountId }: UserRecord): AuthenticatedUser {
  return { id, username, roles, accountId };
}
