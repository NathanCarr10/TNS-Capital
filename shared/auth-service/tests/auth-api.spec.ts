/**
 * HTTP contract tests for the auth service (contracts/auth-api.yaml).
 *
 * Runs the real Nest application - validation, guards, error envelope, JWT
 * signing - with UsersService and RefreshTokensService replaced by in-memory
 * fakes, so no database is needed.
 */
import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { createHash } from 'crypto';
import * as jwt from 'jsonwebtoken';
import request from 'supertest';
import { AppModule, configureApp } from '../src/app.module';
import { validateEnv } from '../src/config/env.validation';
import { hashPassword } from '../src/users/password-hasher';
import { RefreshTokensService } from '../src/users/refresh-tokens.service';
import { RegisteredUser, UserRecord, UsernameTakenError, UsersService } from '../src/users/users.service';

const SECRET = process.env.JWT_SECRET as string;
const ISSUER = 'urn:tns-capital:auth-service';
const ISO_8601 = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;

const TRADER = { username: 'trader', password: 'traderPass123' };
const ADMIN = { username: 'admin', password: 'adminPassword' };

/** The users table, in memory */
const store = new Map<string, UserRecord>();

const fakeUsers = {
  findByUsername: jest.fn(async (username: string) => store.get(username) ?? null),
  findById: jest.fn(async (id: number) => [...store.values()].find((u) => u.id === id) ?? null),
  create: jest.fn(async (username: string): Promise<RegisteredUser> => {
    if (username === 'taken') {
      throw new UsernameTakenError();
    }
    if (username === 'explode') {
      throw new Error('connection refused at db.ts:42');
    }
    return { id: 9, username, createdAt: new Date(), accountId: 42, accountNumber: 'ACC-U000009' };
  }),
};

/** The refresh_tokens table, in memory, keyed by token hash as in Postgres */
const tokenRows = new Map<string, { userId: number; familyId: string; expiresAt: number; usedAt: number | null }>();
const live = (hash: string) => {
  const row = tokenRows.get(hash);
  return row && row.expiresAt > Date.now() ? row : undefined;
};
const revoke = (familyId: string) => {
  for (const [hash, row] of tokenRows) {
    if (row.familyId === familyId) {
      tokenRows.delete(hash);
    }
  }
};

const fakeRefreshTokens = {
  create: jest.fn(async (userId: number, hash: string, familyId: string, ttlSeconds: number) => {
    tokenRows.set(hash, { userId, familyId, expiresAt: Date.now() + ttlSeconds * 1000, usedAt: null });
  }),
  rotate: jest.fn(async (presented: string, next: string, ttlSeconds: number) => {
    const row = live(presented);
    if (!row) {
      return { status: 'invalid' };
    }
    if (row.usedAt !== null) {
      revoke(row.familyId);
      return { status: 'reused', userId: row.userId };
    }
    row.usedAt = Date.now();
    tokenRows.set(next, { ...row, expiresAt: Date.now() + ttlSeconds * 1000, usedAt: null });
    return { status: 'rotated', userId: row.userId };
  }),
  revokeFamily: jest.fn(async (hash: string) => {
    const row = live(hash);
    if (row) {
      revoke(row.familyId);
    }
    return row !== undefined;
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
    });
    store.set('admin', {
      id: 1,
      username: 'admin',
      passwordHash: await hashPassword(ADMIN.password),
      roles: ['ADMIN'],
      accountId: null,
    });

    const moduleRef = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(UsersService)
      .useValue(fakeUsers)
      .overrideProvider(RefreshTokensService)
      .useValue(fakeRefreshTokens)
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

      expect(fakeRefreshTokens.create).toHaveBeenCalledWith(3, sha256(refreshToken), expect.any(String), 7 * 24 * 60 * 60);
    });

    it('signs exactly the claims sub, roles, typ, jti, iss, iat, exp', async () => {
      const decoded = jwt.decode(await tokenFor(TRADER)) as jwt.JwtPayload;

      expect(Object.keys(decoded).sort()).toEqual(['exp', 'iat', 'iss', 'jti', 'roles', 'sub', 'typ']);
      expect(decoded.sub).toBe('trader');
      expect(decoded.typ).toBe('access');
      expect(decoded.iss).toBe(ISSUER);
    });

    it('gives a registered user the USER role', async () => {
      const decoded = jwt.decode(await tokenFor(TRADER)) as jwt.JwtPayload;
      expect(decoded.roles).toEqual(['USER']);
    });

    it('gives the admin user the ADMIN role', async () => {
      const decoded = jwt.decode(await tokenFor(ADMIN)) as jwt.JwtPayload;
      expect(decoded.roles).toEqual(['ADMIN']);
    });

    it('expires the token 15 minutes after it is issued', async () => {
      const decoded = jwt.decode(await tokenFor(TRADER)) as jwt.JwtPayload;

      expect(typeof decoded.iat).toBe('number');
      expect(decoded.exp! - decoded.iat!).toBe(15 * 60);
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
    it('returns a new access token and a new refresh token', async () => {
      const { refreshToken } = await tokensFor(TRADER);

      const response = await refresh(refreshToken).expect(200);

      expect(Object.keys(response.body).sort()).toEqual(['accessToken', 'refreshToken']);
      expect(response.body.refreshToken).not.toBe(refreshToken);
      const decoded = jwt.verify(response.body.accessToken, SECRET, { algorithms: ['HS256'] }) as jwt.JwtPayload;
      expect(decoded.sub).toBe('trader');
      expect(decoded.roles).toEqual(['USER']);
    });

    it('lets each refresh token be used only once', async () => {
      const { refreshToken } = await tokensFor(TRADER);

      await refresh(refreshToken).expect(200);
      const response = await refresh(refreshToken).expect(401);
      expect(response.body.message).toBe('invalid or expired refresh token');
    });

    it('keeps a session going through successive rotations', async () => {
      let { refreshToken } = await tokensFor(TRADER);

      for (let i = 0; i < 3; i++) {
        refreshToken = (await refresh(refreshToken).expect(200)).body.refreshToken;
      }
    });

    it('revokes the whole session when a spent refresh token is reused', async () => {
      const { refreshToken: spent } = await tokensFor(TRADER);
      const { refreshToken: latest } = (await refresh(spent).expect(200)).body;

      // Someone replays the spent token: the latest token of that session stops working too
      await refresh(spent).expect(401);
      await refresh(latest).expect(401);
    });

    it('leaves the user\'s other sessions alone when one is revoked for reuse', async () => {
      const phone = await tokensFor(TRADER);
      const laptop = await tokensFor(TRADER);
      await refresh(phone.refreshToken).expect(200);
      await refresh(phone.refreshToken).expect(401);

      await refresh(laptop.refreshToken).expect(200);
    });

    it('keeps the previous session working when the user logs in again', async () => {
      const first = await tokensFor(TRADER);
      const second = await tokensFor(TRADER);

      await refresh(first.refreshToken).expect(200);
      await refresh(second.refreshToken).expect(200);
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
      tokenRows.get(sha256(refreshToken))!.expiresAt = Date.now() - 1;

      const response = await refresh(refreshToken).expect(401);
      expect(response.body.message).toBe('invalid or expired refresh token');
    });

    it('rejects an access token used as a refresh token', async () => {
      await refresh(await tokenFor(TRADER)).expect(401);
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
      expect(fakeRefreshTokens.rotate).not.toHaveBeenCalled();
    });
  });

  describe('POST /auth/logout', () => {
    it('revokes the session', async () => {
      const { refreshToken } = await tokensFor(TRADER);

      const response = await logout(refreshToken).expect(200);

      expect(response.body).toEqual({ loggedOut: true });
      expect(fakeRefreshTokens.revokeFamily).toHaveBeenCalledWith(sha256(refreshToken));
      await refresh(refreshToken).expect(401);
    });

    it('revokes the later tokens of the session too', async () => {
      const { refreshToken: first } = await tokensFor(TRADER);
      const { refreshToken: latest } = (await refresh(first).expect(200)).body;

      await logout(first).expect(200);
      await refresh(latest).expect(401);
    });

    it('leaves the user\'s other sessions alone', async () => {
      const phone = await tokensFor(TRADER);
      const laptop = await tokensFor(TRADER);

      await logout(phone.refreshToken).expect(200);
      await refresh(laptop.refreshToken).expect(200);
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
      jwt.sign({ sub: 'trader', roles: ['USER'], typ: 'access', jti: 'test-jti', ...claims }, secret, {
        algorithm: 'HS256',
        issuer: ISSUER,
        expiresIn: '15m',
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
      ['the header has extra parts', 'Bearer a.b.c extra'],
      ['the token is not a JWT', 'Bearer not-a-jwt'],
    ])('rejects the request when %s', async (_case, authorization) => {
      const response = await me(authorization).expect(401);
      expect(response.body).toEqual(unauthorised);
    });

    it.each([
      ['has expired', () => sign({}, { expiresIn: -10 })],
      ['is signed with another secret', () => sign({}, {}, 'another-secret-that-is-at-least-32-bytes')],
      ['has another issuer', () => sign({}, { issuer: 'urn:someone-else' })],
      ['has the old stub issuer', () => sign({}, { issuer: 'urn:tns-capital:auth-stub' })],
      ['has no issuer', () => jwt.sign({ sub: 'trader', roles: ['USER'], typ: 'access', jti: 'x' }, SECRET, { algorithm: 'HS256' })],
      ['uses alg "none"', () => jwt.sign({ sub: 'trader', roles: ['USER'], typ: 'access', jti: 'x', iss: ISSUER }, '', { algorithm: 'none' })],
      ['has no roles claim', () => sign({ roles: undefined })],
      ['has no typ claim', () => sign({ typ: undefined })],
      ['is not typ access', () => sign({ typ: 'refresh' })],
      ['has no jti claim', () => sign({ jti: undefined })],
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
