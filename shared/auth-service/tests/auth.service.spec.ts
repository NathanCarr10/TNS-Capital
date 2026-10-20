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
import { EmailTakenError, UserRecord, UsersService } from '../src/users/users.service';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

describe('AuthService', () => {
  let trader: UserRecord;
  const users = {
    findByEmail: jest.fn(),
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
    trader = {
      id: 3,
      email: 'trader@example.com',
      roles: ['USER'],
      accountId: 7,
      passwordHash: await hashPassword('password123'),
    };
  });

  beforeEach(() => jest.clearAllMocks());

  describe('register', () => {
    it('hashes the password with Argon2id before creating the user (AC#1)', async () => {
      users.findByEmail.mockResolvedValue(null);
      users.create.mockResolvedValue({ id: 9 });

      await auth.register(' NewTrader@Example.com ', 'password123', 'Ada');

      const [email, passwordHash, holderName] = users.create.mock.calls[0];
      expect(passwordHash).toMatch(/^\$argon2id\$/);
      expect(passwordHash).not.toContain('password123');
      expect(email).toBe('newtrader@example.com');
      expect(holderName).toBe('Ada');
    });

    it('rejects a registered email with USR-409 before hashing', async () => {
      users.findByEmail.mockResolvedValue(trader);

      await expect(auth.register('Trader@Example.com', 'password123')).rejects.toMatchObject({ errorCode: 'USR-409' });
      expect(users.findByEmail).toHaveBeenCalledWith('trader@example.com');
      expect(users.create).not.toHaveBeenCalled();
    });

    it('maps a concurrent duplicate to USR-409', async () => {
      users.findByEmail.mockResolvedValue(null);
      users.create.mockRejectedValue(new EmailTakenError());

      await expect(auth.register('trader@example.com', 'password123')).rejects.toMatchObject({ errorCode: 'USR-409' });
    });

    it('passes on any other error', async () => {
      const failure = new Error('database down');
      users.findByEmail.mockResolvedValue(null);
      users.create.mockRejectedValue(failure);

      await expect(auth.register('trader@example.com', 'password123')).rejects.toBe(failure);
    });
  });

  describe('login', () => {
    it('issues both tokens and stores the refresh token hash in a new session family', async () => {
      users.findByEmail.mockResolvedValue(trader);

      expect(await auth.login('trader@example.com', 'password123')).toEqual({
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
      });
      expect(refreshTokens.create).toHaveBeenCalledWith(3, 'refresh-hash', expect.stringMatching(UUID), 604800);
    });

    it('starts a different session family on every login', async () => {
      users.findByEmail.mockResolvedValue(trader);

      await auth.login('trader@example.com', 'password123');
      await auth.login('trader@example.com', 'password123');

      const [first, second] = refreshTokens.create.mock.calls.map((call) => call[2]);
      expect(first).not.toBe(second);
    });

    it.each([
      ['the password is wrong', 'wrong-password'],
      ['the user does not exist', 'password123'],
    ])('gives the same AUTH-401 when %s, and stores nothing', async (theCase, password) => {
      users.findByEmail.mockResolvedValue(theCase.includes('exist') ? null : trader);

      const error = (await auth.login('trader@example.com', password).catch((e) => e)) as ApiException;
      expect(error.errorCode).toBe('AUTH-401');
      expect(error.message).toBe('invalid email or password');
      expect(refreshTokens.create).not.toHaveBeenCalled();
    });

    it('logs a successful login with the email and client IP, never the password (A09-2)', async () => {
      users.findByEmail.mockResolvedValue(trader);
      const log = jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);

      await auth.login('trader@example.com', 'password123', '10.0.0.5');

      expect(log).toHaveBeenCalledWith('Login succeeded: email="trader@example.com" id=3 ip=10.0.0.5');
      expect(JSON.stringify(log.mock.calls)).not.toContain('password123');
      log.mockRestore();
    });

    it('logs a failed login, quoting the email so it cannot forge log lines', async () => {
      users.findByEmail.mockResolvedValue(null);
      const warn = jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);

      await auth.login('evil\nLogin succeeded: email="admin@example.com"', 'guess', '10.0.0.6').catch(() => undefined);

      expect(warn).toHaveBeenCalledWith(
        'Login failed: email="evil\\nLogin succeeded: email=\\"admin@example.com\\"" ip=10.0.0.6',
      );
      expect(JSON.stringify(warn.mock.calls)).not.toContain('guess');
      warn.mockRestore();
    });
  });

  describe('refresh', () => {
    it('spends the presented token and returns a new pair with roles from the database', async () => {
      refreshTokens.rotate.mockResolvedValue({ status: 'rotated', userId: 3 });
      users.findById.mockResolvedValue(trader);

      expect(await auth.refresh('old-token')).toEqual({ accessToken: 'access-token', refreshToken: 'refresh-token' });
      expect(refreshTokens.rotate).toHaveBeenCalledWith('hash-of-old-token', 'refresh-hash', 604800);
      expect(users.findById).toHaveBeenCalledWith(3);
      expect(tokens.issue).toHaveBeenCalledWith('trader@example.com', ['USER']);
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

  describe('validate', () => {
    it('returns the user for the right password', async () => {
      users.findByEmail.mockResolvedValue(trader);

      expect(await auth.validate('TRADER@EXAMPLE.COM', 'password123')).toBe(trader);
    });

    it('returns null for a wrong password or an unknown user', async () => {
      users.findByEmail.mockResolvedValue(trader);
      expect(await auth.validate('trader@example.com', 'wrongPassword')).toBeNull();

      users.findByEmail.mockResolvedValue(null);
      expect(await auth.validate('nobody@example.com', 'password123')).toBeNull();
    });
  });

  describe('me', () => {
    const claims = { sub: 'trader@example.com', roles: ['ADMIN' as const], typ: 'access' as const, jti: 'j', iss: 'x', iat: 0, exp: 0 };

    it('returns the current user without the password hash', async () => {
      users.findByEmail.mockResolvedValue(trader);

      expect(await auth.me(claims)).toEqual({ id: 3, email: 'trader@example.com', roles: ['USER'], accountId: 7 });
      expect(users.findByEmail).toHaveBeenCalledWith('trader@example.com');
    });

    it('rejects with AUTH-401 when the user no longer exists', async () => {
      users.findByEmail.mockResolvedValue(null);
      await expect(auth.me(claims)).rejects.toMatchObject({ errorCode: 'AUTH-401' });
    });
  });
});
