/**
 * Brute-force protection on the credential endpoints. Runs the real Nest
 * application with a limit of 3 requests per minute and a users table with
 * nobody in it, so every login fails with AUTH-401 until the limit is reached.
 */
import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getOptionsToken } from '@nestjs/throttler';
import request from 'supertest';
import { AppModule, configureApp } from '../src/app.module';
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
      await request(app.getHttpServer()).post(path).send({}).expect(429);
    }
  });

  it('does not limit the health check', async () => {
    for (let i = 0; i <= LIMIT; i++) {
      await request(app.getHttpServer()).get('/health').expect(200);
    }
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
