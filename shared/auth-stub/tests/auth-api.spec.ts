/**
 * HTTP contract tests for the auth service (contracts/auth-api.yaml).
 *
 * Runs the real Nest application - validation, guards, error envelope, JWT
 * signing - with UsersService replaced by an in-memory fake, so no database
 * is needed.
 */
import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { createHash } from 'crypto';
import * as jwt from 'jsonwebtoken';
import request from 'supertest';
import { AppModule, configureApp } from '../src/app.module';
import { validateEnv } from '../src/config/env.validation';
import { hashPassword } from '../src/users/password-hasher';
import { RegisteredUser, UserRecord, UsernameTakenError, UsersService } from '../src/users/users.service';

const SECRET = process.env.JWT_SECRET as string;
const ISO_8601 = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;

const TRADER = { username: 'trader', password: 'traderPass123' };
const ADMIN = { username: 'admin', password: 'adminPassword' };

/** The users table, in memory. Refresh tokens are stored by hash, as in Postgres. */
const store = new Map<string, UserRecord & { refreshTokenHash: string | null; refreshTokenExpiresAt: number }>();

const fakeUsers = {
  findByUsername: jest.fn(async (username: string) => store.get(username) ?? null),
  create: jest.fn(async (username: string): Promise<RegisteredUser> => {
    if (username === 'taken') {
      throw new UsernameTakenError();
    }
    if (username === 'explode') {
      throw new Error('connection refused at db.ts:42');
    }
    return { id: 9, username, createdAt: new Date(), accountId: 42, accountNumber: 'ACC-U000009' };
  }),
  setRefreshToken: jest.fn(async (userId: number, hash: string, ttlSeconds: number) => {
    const user = [...store.values()].find((u) => u.id === userId)!;
    user.refreshTokenHash = hash;
    user.refreshTokenExpiresAt = Date.now() + ttlSeconds * 1000;
  }),
  findByRefreshTokenHash: jest.fn(
    async (hash: string) =>
      [...store.values()].find((u) => u.refreshTokenHash === hash && u.refreshTokenExpiresAt > Date.now()) ?? null,
  ),
  clearRefreshToken: jest.fn(async (userId: number) => {
    const user = [...store.values()].find((u) => u.id === userId)!;
    user.refreshTokenHash = null;
  }),
};

const sha256 = (value: string) => createHash('sha256').update(value).digest('hex');

describe('Auth API contract', () => {
  let app: INestApplication;

  beforeAll(async () => {
    store.set('trader', {
      id: 3,
      username: 'trader',
      passwordHash: await hashPassword(TRADER.password),
      roles: ['USER'],
      accountId: 7,
      refreshTokenHash: null,
      refreshTokenExpiresAt: 0,
    });
    store.set('admin', {
      id: 1,
      username: 'admin',
      passwordHash: await hashPassword(ADMIN.password),
      roles: ['ADMIN'],
      accountId: null,
      refreshTokenHash: null,
      refreshTokenExpiresAt: 0,
    });

    const moduleRef = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(UsersService)
      .useValue(fakeUsers)
      .compile();
    app = configureApp(moduleRef.createNestApplication({ logger: false }));
    await app.init();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => jest.clearAllMocks());

  const post = (path: string, body: unknown) => request(app.getHttpServer()).post(path).send(body as object);
  const login = (body: unknown) => post('/auth/login', body);
  const register = (body: unknown) => post('/auth/register', body);
  const refresh = (refreshToken: unknown) => post('/auth/refresh', { refreshToken });
  const logout = (refreshToken: unknown) => post('/auth/logout', { refreshToken });
  const me = (authorization?: string) => {
    const req = request(app.getHttpServer()).get('/auth/me');
    return authorization === undefined ? req : req.set('Authorization', authorization);
  };
  const tokensFor = async (credentials: object) =>
    (await login(credentials).expect(200)).body as { accessToken: string; refreshToken: string };
  const tokenFor = async (credentials: object) => (await tokensFor(credentials)).accessToken;

  describe('POST /auth/login - success', () => {
    it('returns only an access token and a refresh token', async () => {
      const response = await login(TRADER).expect(200);

      expect(Object.keys(response.body).sort()).toEqual(['accessToken', 'refreshToken']);
      expect(response.body.accessToken.split('.')).toHaveLength(3);
      expect(response.body.refreshToken).toMatch(/^[A-Za-z0-9_-]{43}$/);
    });

    it('stores only the hash of the refresh token, valid for 7 days', async () => {
      const { refreshToken } = await tokensFor(TRADER);

      expect(fakeUsers.setRefreshToken).toHaveBeenCalledWith(3, sha256(refreshToken), 7 * 24 * 60 * 60);
    });

    it('signs exactly the contract claims: sub, roles, iss, iat, exp', async () => {
      const decoded = jwt.decode(await tokenFor(TRADER)) as jwt.JwtPayload;

      expect(Object.keys(decoded).sort()).toEqual(['exp', 'iat', 'iss', 'roles', 'sub']);
      expect(decoded.sub).toBe('trader');
      expect(decoded.iss).toBe('urn:tns-capital:auth-stub');
    });

    it('gives a registered user the USER role', async () => {
      const decoded = jwt.decode(await tokenFor(TRADER)) as jwt.JwtPayload;
      expect(decoded.roles).toEqual(['USER']);
    });

    it('gives the admin user the ADMIN role', async () => {
      const decoded = jwt.decode(await tokenFor(ADMIN)) as jwt.JwtPayload;
      expect(decoded.roles).toEqual(['ADMIN']);
    });

    it('expires the token 1 hour after it is issued', async () => {
      const decoded = jwt.decode(await tokenFor(TRADER)) as jwt.JwtPayload;

      expect(typeof decoded.iat).toBe('number');
      expect(decoded.exp! - decoded.iat!).toBe(3600);
      expect(decoded.exp!).toBeGreaterThan(Math.floor(Date.now() / 1000));
    });

    it('signs with HS256 and the shared secret', async () => {
      const token = await tokenFor(TRADER);

      expect(jwt.decode(token, { complete: true })!.header.alg).toBe('HS256');
      const verified = jwt.verify(token, SECRET, { algorithms: ['HS256'] }) as jwt.JwtPayload;
      expect(verified.sub).toBe('trader');
    });

    it('never puts the password, hash or database ids in the token', async () => {
      const payload = JSON.stringify(jwt.decode(await tokenFor(TRADER)));

      expect(payload).not.toContain(TRADER.password);
      expect(payload).not.toMatch(/password|hash|created_at|updated_at|accountId|"id"/i);
    });
  });

  describe('POST /auth/login - 401', () => {
    it('rejects a wrong password with AUTH-401 in the platform envelope', async () => {
      const response = await login({ username: 'trader', password: 'wrong' }).expect(401);

      expect(response.body).toEqual({
        errorCode: 'AUTH-401',
        message: 'invalid username or password',
        timestamp: expect.stringMatching(ISO_8601),
      });
    });

    it('gives the same answer for an unknown user, so usernames cannot be probed', async () => {
      const unknown = await login({ username: 'nobody', password: 'wrong' }).expect(401);
      const wrongPassword = await login({ username: 'trader', password: 'wrong' }).expect(401);

      expect(unknown.body.message).toBe(wrongPassword.body.message);
    });
  });

  describe('POST /auth/login - 400 VAL-422 validation', () => {
    it.each([
      ['username is missing', { password: 'test' }],
      ['password is missing', { username: 'trader' }],
      ['both are missing', {}],
    ])('reports missing credentials when %s', async (_case, body) => {
      const response = await login(body).expect(400);

      expect(response.body).toEqual({
        errorCode: 'VAL-422',
        message: 'missing username or password',
        timestamp: expect.stringMatching(ISO_8601),
      });
      expect(fakeUsers.findByUsername).not.toHaveBeenCalled();
    });

    it.each([
      ['username is a number', { username: 123, password: 'test' }],
      ['password is a number', { username: 'trader', password: 123 }],
      ['username is a boolean', { username: true, password: 'test' }],
      ['password is a boolean', { username: 'trader', password: false }],
      ['username is longer than 100 characters', { username: 'a'.repeat(101), password: 'test' }],
      ['password is longer than 256 characters', { username: 'trader', password: 'p'.repeat(257) }],
      ['username is empty', { username: '', password: 'test' }],
      ['password is empty', { username: 'trader', password: '' }],
    ])('rejects the body when %s', async (_case, body) => {
      const response = await login(body).expect(400);

      expect(Object.keys(response.body).sort()).toEqual(['errorCode', 'message', 'timestamp']);
      expect(response.body.errorCode).toBe('VAL-422');
    });

    it('rejects unexpected properties (additionalProperties: false)', async () => {
      const response = await login({ ...TRADER, extra: 'field', other: 1 }).expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body.message).toBe('Unexpected property: extra, other');
    });

    it.each([
      ['invalid JSON', '{ invalid json }'],
      ['a trailing comma', '{"username": "trader", "password": "test",}'],
    ])('rejects a body with %s', async (_case, raw) => {
      const response = await request(app.getHttpServer())
        .post('/auth/login')
        .set('Content-Type', 'application/json')
        .send(raw)
        .expect(400);

      expect(response.body).toEqual({
        errorCode: 'VAL-422',
        message: 'Request body is missing or malformed',
        timestamp: expect.stringMatching(ISO_8601),
      });
    });

    it.each([
      ['a 1-character username', { username: 'a', password: 'test' }],
      ['a 100-character username', { username: 'a'.repeat(100), password: 'test' }],
      ['a 1-character password', { username: 'trader', password: 'x' }],
      ['a 256-character password', { username: 'trader', password: 'p'.repeat(256) }],
    ])('accepts %s (then fails authentication)', async (_case, body) => {
      const response = await login(body).expect(401);
      expect(response.body.errorCode).toBe('AUTH-401');
    });
  });

  describe('POST /auth/register', () => {
    it('returns 201 with the new user and the account opened for them', async () => {
      const response = await register({ username: 'newtrader', password: 'password123' }).expect(201);

      expect(response.body).toEqual({
        id: 9,
        username: 'newtrader',
        createdAt: expect.stringMatching(ISO_8601),
        accountId: 42,
        accountNumber: 'ACC-U000009',
      });
      expect(fakeUsers.create).toHaveBeenCalledWith('newtrader', expect.stringMatching(/^\$argon2id\$/), undefined);
    });

    it('hashes the password before it reaches the database layer', async () => {
      await register({ username: 'newtrader', password: 'password123' }).expect(201);

      expect(JSON.stringify(fakeUsers.create.mock.calls)).not.toContain('password123');
    });

    it('passes the holder name through for the account', async () => {
      await register({ username: 'newtrader', password: 'password123', holderName: 'Ada Lovelace' }).expect(201);

      expect(fakeUsers.create).toHaveBeenCalledWith('newtrader', expect.any(String), 'Ada Lovelace');
    });

    it('never returns the password or its hash', async () => {
      const response = await register({ username: 'newtrader', password: 'password123' }).expect(201);

      expect(JSON.stringify(response.body)).not.toMatch(/password|hash/i);
    });

    it('rejects a duplicate username with 409 USR-409', async () => {
      const response = await register({ username: 'trader', password: 'password123' }).expect(409);

      expect(response.body).toEqual({
        errorCode: 'USR-409',
        message: 'User already exists',
        timestamp: expect.stringMatching(ISO_8601),
      });
      expect(fakeUsers.create).not.toHaveBeenCalled();
    });

    it('rejects a username registered concurrently with 409 USR-409', async () => {
      const response = await register({ username: 'taken', password: 'password123' }).expect(409);
      expect(response.body.errorCode).toBe('USR-409');
    });

    it.each([
      ['username is missing', { password: 'password123' }],
      ['username is shorter than 3 characters', { username: 'ab', password: 'password123' }],
      ['password is shorter than 8 characters', { username: 'newtrader', password: 'short' }],
      ['holderName is blank', { username: 'newtrader', password: 'password123', holderName: '   ' }],
      ['there is an unexpected property', { username: 'newtrader', password: 'password123', role: 'ADMIN' }],
    ])('rejects the body when %s', async (_case, body) => {
      const response = await register(body).expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(fakeUsers.create).not.toHaveBeenCalled();
    });

    it('hides internal errors behind SYS-500', async () => {
      const response = await register({ username: 'explode', password: 'password123' }).expect(500);

      expect(response.body).toEqual({
        errorCode: 'SYS-500',
        message: 'Internal server error',
        timestamp: expect.stringMatching(ISO_8601),
      });
    });
  });

  describe('POST /auth/refresh', () => {
    it('issues a new access token for a valid refresh token', async () => {
      const { refreshToken } = await tokensFor(TRADER);

      const response = await refresh(refreshToken).expect(200);

      expect(Object.keys(response.body)).toEqual(['accessToken']);
      const decoded = jwt.verify(response.body.accessToken, SECRET, { algorithms: ['HS256'] }) as jwt.JwtPayload;
      expect(decoded.sub).toBe('trader');
      expect(decoded.roles).toEqual(['USER']);
    });

    it('keeps the refresh token usable until it expires', async () => {
      const { refreshToken } = await tokensFor(TRADER);

      await refresh(refreshToken).expect(200);
      await refresh(refreshToken).expect(200);
    });

    it('rejects an unknown refresh token with AUTH-401', async () => {
      const response = await refresh('not-a-real-token').expect(401);

      expect(response.body).toEqual({
        errorCode: 'AUTH-401',
        message: 'invalid or expired refresh token',
        timestamp: expect.stringMatching(ISO_8601),
      });
    });

    it('rejects an expired refresh token', async () => {
      const { refreshToken } = await tokensFor(TRADER);
      store.get('trader')!.refreshTokenExpiresAt = Date.now() - 1;

      const response = await refresh(refreshToken).expect(401);
      expect(response.body.message).toBe('invalid or expired refresh token');
    });

    it('rejects an access token used as a refresh token', async () => {
      await refresh(await tokenFor(TRADER)).expect(401);
    });

    it('invalidates the previous refresh token when the user logs in again', async () => {
      const first = await tokensFor(TRADER);
      const second = await tokensFor(TRADER);

      await refresh(first.refreshToken).expect(401);
      await refresh(second.refreshToken).expect(200);
    });

    it.each([
      ['refreshToken is missing', {}, 'missing refresh token'],
      ['refreshToken is not a string', { refreshToken: 123 }, undefined],
      ['refreshToken is empty', { refreshToken: '' }, undefined],
      ['there is an unexpected property', { refreshToken: 'x', extra: 1 }, 'Unexpected property: extra'],
    ])('rejects the body when %s', async (_case, body, message) => {
      const response = await post('/auth/refresh', body).expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      if (message) {
        expect(response.body.message).toBe(message);
      }
      expect(fakeUsers.findByRefreshTokenHash).not.toHaveBeenCalled();
    });
  });

  describe('POST /auth/logout', () => {
    it('revokes the refresh token', async () => {
      const { refreshToken } = await tokensFor(TRADER);

      const response = await logout(refreshToken).expect(200);

      expect(response.body).toEqual({ loggedOut: true });
      expect(fakeUsers.clearRefreshToken).toHaveBeenCalledWith(3);
      await refresh(refreshToken).expect(401);
    });

    it('rejects logging out twice with the same token', async () => {
      const { refreshToken } = await tokensFor(TRADER);
      await logout(refreshToken).expect(200);

      const response = await logout(refreshToken).expect(401);
      expect(response.body).toEqual({
        errorCode: 'AUTH-401',
        message: 'invalid or expired refresh token',
        timestamp: expect.stringMatching(ISO_8601),
      });
    });

    it('rejects an unknown refresh token', async () => {
      await logout('not-a-real-token').expect(401);
      expect(fakeUsers.clearRefreshToken).not.toHaveBeenCalled();
    });

    it('rejects a missing refresh token with VAL-422', async () => {
      const response = await post('/auth/logout', {}).expect(400);
      expect(response.body.message).toBe('missing refresh token');
    });
  });

  describe('GET /auth/me (protected by JwtAuthGuard)', () => {
    const unauthorised = {
      errorCode: 'AUTH-401',
      message: 'Unauthorised or invalid token',
      timestamp: expect.stringMatching(ISO_8601),
    };
    const sign = (claims: object, options: jwt.SignOptions = {}, secret = SECRET) =>
      jwt.sign({ sub: 'trader', roles: ['USER'], ...claims }, secret, {
        algorithm: 'HS256',
        issuer: 'urn:tns-capital:auth-stub',
        expiresIn: '1h',
        ...options,
      });

    it('returns the user the access token belongs to', async () => {
      const response = await me(`Bearer ${await tokenFor(TRADER)}`).expect(200);

      expect(response.body).toEqual({ username: 'trader', roles: ['USER'], accountId: 7 });
    });

    it('returns an admin with no account', async () => {
      const response = await me(`Bearer ${await tokenFor(ADMIN)}`).expect(200);

      expect(response.body).toEqual({ username: 'admin', roles: ['ADMIN'], accountId: null });
    });

    it('accepts an access token obtained through refresh', async () => {
      const { refreshToken } = await tokensFor(TRADER);
      const { accessToken } = (await refresh(refreshToken).expect(200)).body;

      await me(`Bearer ${accessToken}`).expect(200);
    });

    it.each([
      ['there is no Authorization header', undefined],
      ['the scheme is not Bearer', 'Basic dHJhZGVyOnBhc3M='],
      ['the Bearer token is empty', 'Bearer '],
      ['the token is not a JWT', 'Bearer not-a-jwt'],
    ])('rejects the request when %s', async (_case, authorization) => {
      const response = await me(authorization).expect(401);
      expect(response.body).toEqual(unauthorised);
    });

    it.each([
      ['has expired', () => sign({}, { expiresIn: -10 })],
      ['is signed with another secret', () => sign({}, {}, 'another-secret-that-is-at-least-32-bytes')],
      ['has another issuer', () => sign({}, { issuer: 'urn:someone-else' })],
      ['has no issuer', () => jwt.sign({ sub: 'trader', roles: ['USER'] }, SECRET, { algorithm: 'HS256' })],
      ['uses alg "none"', () => jwt.sign({ sub: 'trader', roles: ['USER'], iss: 'urn:tns-capital:auth-stub' }, '', { algorithm: 'none' })],
      ['has no roles claim', () => sign({ roles: undefined })],
      ['belongs to a user who no longer exists', () => sign({ sub: 'deleted-user' })],
    ])('rejects a token that %s', async (_case, makeToken) => {
      const response = await me(`Bearer ${makeToken()}`).expect(401);
      expect(response.body).toEqual(unauthorised);
    });

    it('rejects a refresh token used as an access token', async () => {
      const { refreshToken } = await tokensFor(TRADER);
      await me(`Bearer ${refreshToken}`).expect(401);
    });

    it('takes roles from the database, not the token', async () => {
      const response = await me(`Bearer ${sign({ roles: ['ADMIN'] })}`).expect(200);
      expect(response.body.roles).toEqual(['USER']);
    });

    it('accepts the scheme in any case', async () => {
      await me(`bearer ${await tokenFor(TRADER)}`).expect(200);
    });
  });

  describe('GET /health', () => {
    it('returns only the status', async () => {
      const response = await request(app.getHttpServer()).get('/health').expect(200);
      expect(response.body).toEqual({ status: 'up' });
    });
  });

  describe('Unknown routes and methods', () => {
    it.each([
      ['GET', '/unknown-route'],
      ['POST', '/unknown'],
      ['POST', '/login'],
      ['POST', '/register'],
      ['POST', '/api/auth/login'],
      ['GET', '/v1/health'],
    ])('%s %s returns 404 NOT-404', async (method, path) => {
      const response = await request(app.getHttpServer())[method.toLowerCase() as 'get' | 'post'](path).expect(404);

      expect(Object.keys(response.body).sort()).toEqual(['errorCode', 'message', 'timestamp']);
      expect(response.body.errorCode).toBe('NOT-404');
    });

    it.each([
      ['POST', '/health'],
      ['GET', '/auth/login'],
      ['GET', '/auth/register'],
      ['GET', '/auth/refresh'],
      ['GET', '/auth/logout'],
      ['POST', '/auth/me'],
    ])('%s %s returns 405 REQ-405', async (method, path) => {
      const response = await request(app.getHttpServer())[method.toLowerCase() as 'get' | 'post'](path).expect(405);

      expect(Object.keys(response.body).sort()).toEqual(['errorCode', 'message', 'timestamp']);
      expect(response.body.errorCode).toBe('REQ-405');
    });
  });
});

describe('JWT_SECRET validation', () => {
  it('refuses to start without JWT_SECRET', () => {
    expect(() => validateEnv({})).toThrow('JWT_SECRET is not set');
    expect(() => validateEnv({ JWT_SECRET: '' })).toThrow('JWT_SECRET is not set');
  });

  it('refuses a secret shorter than 32 bytes', () => {
    expect(() => validateEnv({ JWT_SECRET: 'too-short' })).toThrow('at least 32 bytes');
  });

  it('accepts a secret of at least 32 bytes', () => {
    const env = { JWT_SECRET: 'x'.repeat(32) };
    expect(validateEnv(env)).toBe(env);
  });
});
