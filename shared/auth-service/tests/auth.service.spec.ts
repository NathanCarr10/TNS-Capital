/**
 * AuthService with UsersService, RefreshTokensService and TokenService
 * mocked: the decisions it makes, independent of HTTP and the database.
 */
import { Logger } from '@nestjs/common';
import { AuthService } from '../src/auth/auth.service';
import { TokenService } from '../src/auth/token.service';
import { ApiException } from '../src/common/api.exception';
import { hashPassword } from '../src/users/password-hasher';
import { RefreshTokensService } from '../src/users/refresh-tokens.service';
import { UserRecord, UsernameTakenError, UsersService } from '../src/users/users.service';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

describe('AuthService', () => {
  let trader: UserRecord;
  const users = {
    findByUsername: jest.fn(),
    findById: jest.fn(),
    create: jest.fn(),
  };
  const refreshTokens = {
    create: jest.fn(),
    rotate: jest.fn(),
    revokeFamily: jest.fn(),
  };
  const tokens = {
    issue: jest.fn(async () => 'access-token'),
    generateRefreshToken: jest.fn(() => ({ token: 'refresh-token', hash: 'refresh-hash' })),
    hashRefreshToken: jest.fn((token: string) => `hash-of-${token}`),
  };
  const auth = new AuthService(
    users as unknown as UsersService,
    tokens as unknown as TokenService,
    refreshTokens as unknown as RefreshTokensService,
  );

  beforeAll(async () => {
    trader = { id: 3, username: 'trader', roles: ['USER'], accountId: 7, passwordHash: await hashPassword('password123') };
  });

  beforeEach(() => jest.clearAllMocks());

  describe('register', () => {
    it('hashes the password with Argon2id before creating the user (AC#1)', async () => {
      users.findByUsername.mockResolvedValue(null);
      users.create.mockResolvedValue({ id: 9 });

      await auth.register('newtrader', 'password123', 'Ada');

      const [, passwordHash, holderName] = users.create.mock.calls[0];
      expect(passwordHash).toMatch(/^\$argon2id\$/);
      expect(passwordHash).not.toContain('password123');
      expect(holderName).toBe('Ada');
    });

    it('maps a concurrent duplicate to USR-409', async () => {
      users.findByUsername.mockResolvedValue(null);
      users.create.mockRejectedValue(new UsernameTakenError());

      await expect(auth.register('trader', 'password123')).rejects.toMatchObject({ errorCode: 'USR-409' });
    });

    it('passes on any other error', async () => {
      const failure = new Error('database down');
      users.findByUsername.mockResolvedValue(null);
      users.create.mockRejectedValue(failure);

      await expect(auth.register('trader', 'password123')).rejects.toBe(failure);
    });
  });

  describe('login', () => {
    it('issues both tokens and stores the refresh token hash in a new session family', async () => {
      users.findByUsername.mockResolvedValue(trader);

      expect(await auth.login('trader', 'password123')).toEqual({
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
      });
      expect(refreshTokens.create).toHaveBeenCalledWith(3, 'refresh-hash', expect.stringMatching(UUID), 604800);
    });

    it('starts a different session family on every login', async () => {
      users.findByUsername.mockResolvedValue(trader);

      await auth.login('trader', 'password123');
      await auth.login('trader', 'password123');

      const [first, second] = refreshTokens.create.mock.calls.map((call) => call[2]);
      expect(first).not.toBe(second);
    });

    it.each([
      ['the password is wrong', 'wrong-password'],
      ['the user does not exist', 'password123'],
    ])('gives the same AUTH-401 when %s, and stores nothing', async (theCase, password) => {
      users.findByUsername.mockResolvedValue(theCase.includes('exist') ? null : trader);

      const error = (await auth.login('trader', password).catch((e) => e)) as ApiException;
      expect(error.errorCode).toBe('AUTH-401');
      expect(error.message).toBe('invalid username or password');
      expect(refreshTokens.create).not.toHaveBeenCalled();
    });
  });

  describe('refresh', () => {
    it('spends the presented token and returns a new pair with roles from the database', async () => {
      refreshTokens.rotate.mockResolvedValue({ status: 'rotated', userId: 3 });
      users.findById.mockResolvedValue(trader);

      expect(await auth.refresh('old-token')).toEqual({ accessToken: 'access-token', refreshToken: 'refresh-token' });
      expect(refreshTokens.rotate).toHaveBeenCalledWith('hash-of-old-token', 'refresh-hash', 604800);
      expect(users.findById).toHaveBeenCalledWith(3);
      expect(tokens.issue).toHaveBeenCalledWith(trader);
    });

    it('rejects a reused token with AUTH-401 and logs the reuse', async () => {
      refreshTokens.rotate.mockResolvedValue({ status: 'reused', userId: 3 });
      const warn = jest.spyOn(Logger.prototype, 'warn').mockImplementation();

      await expect(auth.refresh('spent-token')).rejects.toMatchObject({
        errorCode: 'AUTH-401',
        message: 'invalid or expired refresh token',
      });
      expect(warn).toHaveBeenCalledWith(expect.stringContaining('reuse detected for user id=3'));
      expect(warn.mock.calls[0][0]).not.toContain('spent-token');
      expect(tokens.issue).not.toHaveBeenCalled();
      warn.mockRestore();
    });

    it('rejects an unknown or expired token with AUTH-401', async () => {
      refreshTokens.rotate.mockResolvedValue({ status: 'invalid' });

      await expect(auth.refresh('unknown')).rejects.toMatchObject({ errorCode: 'AUTH-401' });
      expect(users.findById).not.toHaveBeenCalled();
    });

    it('rejects the token when its user no longer exists', async () => {
      refreshTokens.rotate.mockResolvedValue({ status: 'rotated', userId: 3 });
      users.findById.mockResolvedValue(null);

      await expect(auth.refresh('old-token')).rejects.toMatchObject({ errorCode: 'AUTH-401' });
      expect(tokens.issue).not.toHaveBeenCalled();
    });
  });

  describe('logout', () => {
    it('revokes the session the token belongs to', async () => {
      refreshTokens.revokeFamily.mockResolvedValue(true);

      expect(await auth.logout('refresh-token')).toEqual({ loggedOut: true });
      expect(refreshTokens.revokeFamily).toHaveBeenCalledWith('hash-of-refresh-token');
    });

    it('rejects an unknown token with AUTH-401', async () => {
      refreshTokens.revokeFamily.mockResolvedValue(false);

      await expect(auth.logout('unknown')).rejects.toMatchObject({
        errorCode: 'AUTH-401',
        message: 'invalid or expired refresh token',
      });
    });
  });

  describe('me', () => {
    const claims = { sub: 'trader', roles: ['ADMIN' as const], typ: 'access' as const, jti: 'j', iss: 'x', iat: 0, exp: 0 };

    it('returns the current user without the password hash', async () => {
      users.findByUsername.mockResolvedValue(trader);

      expect(await auth.me(claims)).toEqual({ id: 3, username: 'trader', roles: ['USER'], accountId: 7 });
    });

    it('rejects with AUTH-401 when the user no longer exists', async () => {
      users.findByUsername.mockResolvedValue(null);
      await expect(auth.me(claims)).rejects.toMatchObject({ errorCode: 'AUTH-401' });
    });
  });
});
