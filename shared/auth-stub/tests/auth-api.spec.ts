/**
 * HTTP contract tests for the auth service (contracts/auth-api.yaml).
 *
 * Runs the real Nest application - validation, error envelope, JWT signing -
 * with UsersService replaced by an in-memory fake, so no database is needed.
 */
import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import * as jwt from 'jsonwebtoken';
import request from 'supertest';
import { AppModule, configureApp } from '../src/app.module';
import { validateEnv } from '../src/config/env.validation';
import { AuthenticatedUser, RegisteredUser, UsernameTakenError, UsersService } from '../src/users/users.service';

const SECRET = process.env.JWT_SECRET as string;
const ISO_8601 = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;

const TRADER = { username: 'trader', password: 'traderPass123' };
const ADMIN = { username: 'admin', password: 'adminPassword' };

const knownUsers: Record<string, { password: string; user: AuthenticatedUser }> = {
  trader: { password: TRADER.password, user: { id: 3, username: 'trader', roles: ['USER'], accountId: 7 } },
  admin: { password: ADMIN.password, user: { id: 1, username: 'admin', roles: ['ADMIN'], accountId: null } },
};

const fakeUsers = {
  authenticate: jest.fn(async (username: string, password: string) => {
    const known = knownUsers[username];
    return known && known.password === password ? known.user : null;
  }),
  register: jest.fn(async (username: string): Promise<RegisteredUser> => {
    if (username === 'taken') {
      throw new UsernameTakenError();
    }
    if (username === 'explode') {
      throw new Error('connection refused at db.ts:42');
    }
    return { id: 9, username, createdAt: new Date(), accountId: 42, accountNumber: 'ACC-U000009' };
  }),
};

describe('Auth API contract', () => {
  let app: INestApplication;

  beforeAll(async () => {
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

  const login = (body: unknown) => request(app.getHttpServer()).post('/login').send(body as object);
  const register = (body: unknown) => request(app.getHttpServer()).post('/register').send(body as object);
  const tokenFor = async (credentials: object) => (await login(credentials).expect(200)).body.token as string;

  describe('POST /login - success', () => {
    it('returns only a token', async () => {
      const response = await login(TRADER).expect(200);

      expect(Object.keys(response.body)).toEqual(['token']);
      expect(response.body.token.split('.')).toHaveLength(3);
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

  describe('POST /login - 401', () => {
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

  describe('POST /login - 400 VAL-422 validation', () => {
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
      expect(fakeUsers.authenticate).not.toHaveBeenCalled();
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
        .post('/login')
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

  describe('POST /register', () => {
    it('returns 201 with the new user and the account opened for them', async () => {
      const response = await register({ username: 'newtrader', password: 'password123' }).expect(201);

      expect(response.body).toEqual({
        id: 9,
        username: 'newtrader',
        createdAt: expect.stringMatching(ISO_8601),
        accountId: 42,
        accountNumber: 'ACC-U000009',
      });
      expect(fakeUsers.register).toHaveBeenCalledWith('newtrader', 'password123', undefined);
    });

    it('passes the holder name through for the account', async () => {
      await register({ username: 'newtrader', password: 'password123', holderName: 'Ada Lovelace' }).expect(201);

      expect(fakeUsers.register).toHaveBeenCalledWith('newtrader', 'password123', 'Ada Lovelace');
    });

    it('never returns the password or its hash', async () => {
      const response = await register({ username: 'newtrader', password: 'password123' }).expect(201);

      expect(JSON.stringify(response.body)).not.toMatch(/password|hash/i);
    });

    it('rejects a duplicate username with 409 USR-409', async () => {
      const response = await register({ username: 'taken', password: 'password123' }).expect(409);

      expect(response.body).toEqual({
        errorCode: 'USR-409',
        message: 'User already exists',
        timestamp: expect.stringMatching(ISO_8601),
      });
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
      expect(fakeUsers.register).not.toHaveBeenCalled();
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
      ['POST', '/api/login'],
      ['GET', '/v1/health'],
    ])('%s %s returns 404 NOT-404', async (method, path) => {
      const response = await request(app.getHttpServer())[method.toLowerCase() as 'get' | 'post'](path).expect(404);

      expect(Object.keys(response.body).sort()).toEqual(['errorCode', 'message', 'timestamp']);
      expect(response.body.errorCode).toBe('NOT-404');
    });

    it.each([
      ['POST', '/health'],
      ['GET', '/login'],
      ['GET', '/register'],
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
