/**
 * Brute-force protection on the credential endpoints. Runs the real Nest
 * application with a limit of 3 requests per minute and a users table with
 * nobody in it, so every login fails with AUTH-401 until the limit is reached.
 * The limit is per client, also when clients come through the frontend's proxy.
 */
import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getOptionsToken } from '@nestjs/throttler';
import request from 'supertest';
import { AppModule, configureApp } from '../src/app.module';
import { DEFAULT_TRUST_PROXY, parseTrustProxy } from '../src/auth/rate-limit';
import { validateEnv } from '../src/config/env.validation';
import { RefreshTokensService } from '../src/users/refresh-tokens.service';
import { UsersService } from '../src/users/users.service';

const LIMIT = 3;
const WRONG_LOGIN = { username: 'nobody', password: 'wrongPassword' };

describe('Rate limiting', () => {
  let app: INestApplication;

  beforeEach(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(UsersService)
      .useValue({ findByUsername: jest.fn(async () => null) })
      .overrideProvider(RefreshTokensService)
      .useValue({})
      .overrideProvider(getOptionsToken())
      .useValue([{ ttl: 60_000, limit: LIMIT }])
      .compile();
    app = configureApp(moduleRef.createNestApplication({ logger: false }));
    await app.init();
  });

  afterEach(async () => {
    await app.close();
  });

  it('answers 429 AUTH-429 with Retry-After once a client exceeds the login limit', async () => {
    for (let i = 0; i < LIMIT; i++) {
      await request(app.getHttpServer()).post('/auth/login').send(WRONG_LOGIN).expect(401);
    }

    const response = await request(app.getHttpServer()).post('/auth/login').send(WRONG_LOGIN).expect(429);

    expect(response.body.errorCode).toBe('AUTH-429');
    expect(Number(response.headers['retry-after'])).toBeGreaterThan(0);
  });

  it('limits register and refresh too', async () => {
    for (const path of ['/auth/register', '/auth/refresh']) {
      for (let i = 0; i < LIMIT; i++) {
        await request(app.getHttpServer()).post(path).send({});
      }
      const response = await request(app.getHttpServer()).post(path).send({}).expect(429);
      expect(response.body.errorCode).toBe('AUTH-429');
    }
  });

  it('limits each client behind the frontend proxy separately', async () => {
    // nginx adds the browser's address to X-Forwarded-For; the request itself
    // comes from nginx on the Docker network (here: loopback)
    for (let i = 0; i < LIMIT; i++) {
      await request(app.getHttpServer()).post('/auth/login').set('X-Forwarded-For', '203.0.113.1').send(WRONG_LOGIN);
    }
    const limited = await request(app.getHttpServer())
      .post('/auth/login')
      .set('X-Forwarded-For', '203.0.113.1')
      .send(WRONG_LOGIN);
    const otherClient = await request(app.getHttpServer())
      .post('/auth/login')
      .set('X-Forwarded-For', '203.0.113.2')
      .send(WRONG_LOGIN);

    expect(limited.status).toBe(429);
    expect(otherClient.status).toBe(401);
  });

  it('does not limit the health check', async () => {
    const statuses: number[] = [];
    for (let i = 0; i <= LIMIT; i++) {
      statuses.push((await request(app.getHttpServer()).get('/health')).status);
    }
    expect(statuses).toEqual(Array(LIMIT + 1).fill(200));
  });
});

describe('AUTH_RATE_LIMIT validation', () => {
  const secret = 'x'.repeat(32);

  it('accepts a positive whole number', () => {
    expect(() => validateEnv({ JWT_SECRET: secret, AUTH_RATE_LIMIT: '20' })).not.toThrow();
  });

  it.each(['0', '-1', '2.5', 'ten'])('refuses %s', (value) => {
    expect(() => validateEnv({ JWT_SECRET: secret, AUTH_RATE_LIMIT: value })).toThrow('AUTH_RATE_LIMIT');
  });
});

describe('AUTH_TRUST_PROXY', () => {
  const secret = 'x'.repeat(32);

  it('trusts the loopback and private networks (nginx on the Docker network) by default', () => {
    expect(parseTrustProxy(undefined)).toBe(DEFAULT_TRUST_PROXY);
  });

  it.each([
    ['false', false],
    ['true', true],
    ['2', 2],
    [' 10.0.0.0/8, 172.16.0.0/12 ', '10.0.0.0/8, 172.16.0.0/12'],
    ['192.168.1.1', '192.168.1.1'],
  ])('reads %s', (value, expected) => {
    expect(parseTrustProxy(value)).toEqual(expected);
    expect(() => validateEnv({ JWT_SECRET: secret, AUTH_TRUST_PROXY: value })).not.toThrow();
  });

  it.each(['', '-1', '1.5', 'loopback,,uniquelocal'])('refuses %p', (value) => {
    expect(() => validateEnv({ JWT_SECRET: secret, AUTH_TRUST_PROXY: value })).toThrow('AUTH_TRUST_PROXY');
  });

  it('refuses a value that is not text', () => {
    expect(() => validateEnv({ JWT_SECRET: secret, AUTH_TRUST_PROXY: { hops: 1 } })).toThrow('AUTH_TRUST_PROXY');
  });
});
