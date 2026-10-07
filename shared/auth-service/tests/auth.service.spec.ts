/**
 * AuthService with UsersService and TokenService mocked: the decisions it
 * makes, independent of HTTP and the database.
 */
import { AuthService } from '../src/auth/auth.service';
import { TokenService } from '../src/auth/token.service';
import { ApiException } from '../src/common/api.exception';
import { hashPassword } from '../src/users/password-hasher';
import { UserRecord, UsernameTakenError, UsersService } from '../src/users/users.service';

describe('AuthService', () => {
  let trader: UserRecord;
  const users = {
    findByUsername: jest.fn(),
    create: jest.fn(),
    setRefreshToken: jest.fn(),
    findByRefreshTokenHash: jest.fn(),
    clearRefreshToken: jest.fn(),
  };
  const tokens = {
    issue: jest.fn(async () => 'access-token'),
    generateRefreshToken: jest.fn(() => ({ token: 'refresh-token', hash: 'refresh-hash' })),
    hashRefreshToken: jest.fn((token: string) => `hash-of-${token}`),
  };
  const auth = new AuthService(users as unknown as UsersService, tokens as unknown as TokenService);

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
    it('issues both tokens and stores the refresh token hash', async () => {
      users.findByUsername.mockResolvedValue(trader);

      expect(await auth.login('trader', 'password123')).toEqual({
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
      });
      expect(users.setRefreshToken).toHaveBeenCalledWith(3, 'refresh-hash', 604800);
    });

    it.each([
      ['the password is wrong', 'wrong-password'],
      ['the user does not exist', 'password123'],
    ])('gives the same AUTH-401 when %s, and stores nothing', async (theCase, password) => {
      users.findByUsername.mockResolvedValue(theCase.includes('exist') ? null : trader);

      const error = (await auth.login('trader', password).catch((e) => e)) as ApiException;
      expect(error.errorCode).toBe('AUTH-401');
      expect(error.message).toBe('invalid username or password');
      expect(users.setRefreshToken).not.toHaveBeenCalled();
    });
  });

  describe('refresh and logout', () => {
    it('looks the refresh token up by its hash', async () => {
      users.findByRefreshTokenHash.mockResolvedValue(trader);

      expect(await auth.refresh('refresh-token')).toEqual({ accessToken: 'access-token' });
      expect(users.findByRefreshTokenHash).toHaveBeenCalledWith('hash-of-refresh-token');
    });

    it('clears the stored refresh token on logout', async () => {
      users.findByRefreshTokenHash.mockResolvedValue(trader);

      expect(await auth.logout('refresh-token')).toEqual({ loggedOut: true });
      expect(users.clearRefreshToken).toHaveBeenCalledWith(3);
    });

    it.each(['refresh', 'logout'] as const)('%s rejects an unknown token with AUTH-401', async (method) => {
      users.findByRefreshTokenHash.mockResolvedValue(null);

      await expect(auth[method]('unknown')).rejects.toMatchObject({
        errorCode: 'AUTH-401',
        message: 'invalid or expired refresh token',
      });
      expect(users.clearRefreshToken).not.toHaveBeenCalled();
    });
  });

  describe('validate', () => {
    const payload = { sub: 'trader', roles: ['ADMIN' as const], iss: 'x', iat: 0, exp: 0 };

    it('returns the current user without the password hash', async () => {
      users.findByUsername.mockResolvedValue(trader);

      expect(await auth.validate(payload)).toEqual({ id: 3, username: 'trader', roles: ['USER'], accountId: 7 });
    });

    it('returns null when the user no longer exists', async () => {
      users.findByUsername.mockResolvedValue(null);
      expect(await auth.validate(payload)).toBeNull();
    });
  });
});
