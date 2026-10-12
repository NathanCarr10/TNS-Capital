/**
 * JwtAuthGuard and RolesGuard on their own, with hand-built execution
 * contexts. JwtAuthGuard runs with the real TokenService and real tokens. The
 * HTTP tests (auth-api.spec.ts) run it end to end; no route uses @Roles() yet,
 * so RolesGuard is only exercised here.
 */
import { Controller, ExecutionContext, Get } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { JwtService } from '@nestjs/jwt';
import http from 'http';
import https from 'https';
import * as jwt from 'jsonwebtoken';
import net from 'net';
import { Public } from '../src/auth/decorators/public.decorator';
import { Roles } from '../src/auth/decorators/roles.decorator';
import { JwtAuthGuard } from '../src/auth/guards/jwt-auth.guard';
import { RolesGuard } from '../src/auth/guards/roles.guard';
import { ACCESS_TOKEN_TTL_SECONDS, JWT_ISSUER, TokenService } from '../src/auth/token.service';
import { ApiException } from '../src/common/api.exception';
import { AuthenticatedUser } from '../src/users/users.service';

const SECRET = 'guard-test-shared-secret-at-least-32-bytes';

const TRADER: AuthenticatedUser = { id: 3, username: 'trader', roles: ['USER'], accountId: 7 };
const ADMIN: AuthenticatedUser = { id: 1, username: 'admin', roles: ['ADMIN'], accountId: null };

@Controller()
class SampleController {
  @Get()
  anyUser(): void {}

  @Public()
  @Get()
  open(): void {}

  @Roles('ADMIN')
  @Get()
  adminOnly(): void {}

  @Roles('USER', 'ADMIN')
  @Get()
  userOrAdmin(): void {}
}

@Public()
@Controller()
class PublicController {
  @Get()
  handler(): void {}
}

function contextFor(
  controller: new () => object,
  handler: string,
  request: { headers?: Record<string, string>; user?: AuthenticatedUser } = {},
): { context: ExecutionContext; request: { headers: Record<string, string>; user?: AuthenticatedUser } } {
  const req = { headers: {}, ...request };
  const context = {
    getClass: () => controller,
    getHandler: () => (controller.prototype as Record<string, unknown>)[handler],
    switchToHttp: () => ({ getRequest: () => req }),
  } as unknown as ExecutionContext;
  return { context, request: req };
}

async function errorFrom(promise: Promise<unknown> | (() => unknown)): Promise<ApiException> {
  try {
    await (typeof promise === 'function' ? promise() : promise);
  } catch (error) {
    return error as ApiException;
  }
  throw new Error('expected an error');
}

describe('JwtAuthGuard', () => {
  const tokens = new TokenService(
    new JwtService({
      secret: SECRET,
      signOptions: { algorithm: 'HS256', expiresIn: ACCESS_TOKEN_TTL_SECONDS, issuer: JWT_ISSUER },
    }),
  );
  const guard = new JwtAuthGuard(new Reflector(), tokens);
  const issue = () => tokens.issue({ username: 'trader', roles: ['USER'] });

  /** A token with the service's claims, then overrides; signed with SECRET unless told otherwise */
  const sign = (claims: object = {}, options: jwt.SignOptions = {}, secret = SECRET) =>
    jwt.sign({ sub: 'trader', roles: ['USER'], typ: 'access', ...claims }, secret, {
      algorithm: 'HS256',
      issuer: JWT_ISSUER,
      expiresIn: '15m',
      jwtid: 'jti-1',
      ...options,
    });
  const withToken = (authorization?: string) =>
    contextFor(SampleController, 'anyUser', { headers: authorization === undefined ? {} : { authorization } });

  it.each([
    ['a handler', SampleController, 'open'],
    ['a controller', PublicController, 'handler'],
  ])('lets a route through without a token when %s is @Public()', async (_case, controller, handler) => {
    const { context } = contextFor(controller, handler);

    expect(await guard.canActivate(context)).toBe(true);
  });

  it('lets a valid token through and gives the handler the verified claims', async () => {
    const { context, request } = withToken(`Bearer ${await issue()}`);

    expect(await guard.canActivate(context)).toBe(true);
    expect(request.user).toEqual({
      sub: 'trader',
      roles: ['USER'],
      typ: 'access',
      jti: expect.any(String),
      iss: JWT_ISSUER,
      iat: expect.any(Number),
      exp: expect.any(Number),
    });
  });

  it('accepts the Bearer scheme in any case', async () => {
    expect(await guard.canActivate(withToken(`bearer ${await issue()}`).context)).toBe(true);
  });

  it('rejects an expired token with AUTH-401', async () => {
    const error = await errorFrom(guard.canActivate(withToken(`Bearer ${sign({}, { expiresIn: -10 })}`).context));

    expect(error.getStatus()).toBe(401);
    expect(error.errorCode).toBe('AUTH-401');
  });

  it('rejects a token signed with the wrong secret with AUTH-401', async () => {
    const forged = sign({}, {}, 'another-secret-that-is-at-least-32-bytes-long');
    const error = await errorFrom(guard.canActivate(withToken(`Bearer ${forged}`).context));

    expect(error.getStatus()).toBe(401);
    expect(error.errorCode).toBe('AUTH-401');
  });

  it('rejects a token whose payload was tampered with', async () => {
    const [header, , signature] = (await issue()).split('.');
    const escalated = Buffer.from(
      JSON.stringify({ sub: 'trader', roles: ['ADMIN'], typ: 'access', jti: 'x', iss: JWT_ISSUER }),
    ).toString('base64url');

    const error = await errorFrom(guard.canActivate(withToken(`Bearer ${header}.${escalated}.${signature}`).context));
    expect(error.errorCode).toBe('AUTH-401');
  });

  it.each([
    ['has another issuer', () => sign({}, { issuer: 'urn:someone-else' })],
    ['is not typed as an access token', () => sign({ typ: 'refresh' })],
    [
      'uses alg "none"',
      () => jwt.sign({ sub: 'trader', roles: ['ADMIN'], typ: 'access', jti: 'x', iss: JWT_ISSUER }, '', { algorithm: 'none' }),
    ],
    ['is not a JWT', () => 'not-a-jwt'],
    ['has only two segments', () => 'abc.def'],
  ])('rejects a token that %s', async (_case, makeToken) => {
    const error = await errorFrom(guard.canActivate(withToken(`Bearer ${makeToken()}`).context));
    expect(error.errorCode).toBe('AUTH-401');
  });

  it.each([
    ['no Authorization header', undefined],
    ['a non-Bearer scheme', 'Basic dHJhZGVyOnBhc3M='],
    ['a Bearer scheme with no token', 'Bearer'],
    ['an empty Bearer token', 'Bearer '],
    ['a Bearer header with extra parts', 'Bearer abc.def.ghi extra'],
  ])('rejects %s with AUTH-401', async (_case, authorization) => {
    const { context, request } = withToken(authorization);

    const error = await errorFrom(guard.canActivate(context));
    expect(error.getStatus()).toBe(401);
    expect(error.errorCode).toBe('AUTH-401');
    expect(request.user).toBeUndefined();
  });

  it('gives the same status, code and message whatever the reason', async () => {
    const reasons = [
      undefined,
      'Basic abc',
      'Bearer not-a-jwt',
      `Bearer ${sign({}, { expiresIn: -10 })}`,
      `Bearer ${sign({}, {}, 'another-secret-that-is-at-least-32-bytes-long')}`,
      `Bearer ${sign({}, { issuer: 'urn:someone-else' })}`,
    ];

    for (const authorization of reasons) {
      const error = await errorFrom(guard.canActivate(withToken(authorization).context));
      expect([error.getStatus(), error.errorCode, error.message]).toEqual([
        401,
        'AUTH-401',
        'Unauthorised or invalid token',
      ]);
    }
  });

  describe('verification is local', () => {
    afterEach(() => jest.restoreAllMocks());

    it('depends only on the token service: no users service, database or HTTP client', () => {
      expect(Reflect.getMetadata('design:paramtypes', JwtAuthGuard)).toEqual([Reflector, TokenService]);
      expect(Reflect.getMetadata('design:paramtypes', TokenService)).toEqual([JwtService]);
    });

    it('makes no network call while checking good and bad tokens', async () => {
      const spies = [
        jest.spyOn(http, 'request'),
        jest.spyOn(https, 'request'),
        jest.spyOn(net, 'connect'),
        jest.spyOn(net, 'createConnection'),
        jest.spyOn(globalThis, 'fetch'),
      ];

      await guard.canActivate(withToken(`Bearer ${await issue()}`).context);
      await errorFrom(guard.canActivate(withToken(`Bearer ${sign({}, { expiresIn: -10 })}`).context));

      for (const spy of spies) {
        expect(spy).not.toHaveBeenCalled();
      }
    });
  });
});

describe('RolesGuard', () => {
  const guard = new RolesGuard(new Reflector());

  it('allows any authenticated user on a route without @Roles()', () => {
    const { context } = contextFor(SampleController, 'anyUser', { user: TRADER });
    expect(guard.canActivate(context)).toBe(true);
  });

  it('allows a user holding the required role', () => {
    const { context } = contextFor(SampleController, 'adminOnly', { user: ADMIN });
    expect(guard.canActivate(context)).toBe(true);
  });

  it('allows a user holding any one of several roles', () => {
    expect(guard.canActivate(contextFor(SampleController, 'userOrAdmin', { user: TRADER }).context)).toBe(true);
    expect(guard.canActivate(contextFor(SampleController, 'userOrAdmin', { user: ADMIN }).context)).toBe(true);
  });

  it('rejects a user without the required role with 403 AUTH-403', async () => {
    const { context } = contextFor(SampleController, 'adminOnly', { user: TRADER });

    const error = await errorFrom(() => guard.canActivate(context));
    expect(error.getStatus()).toBe(403);
    expect(error.errorCode).toBe('AUTH-403');
    expect(error.message).toBe('You do not have permission to access this resource');
  });

  it('rejects a request with no authenticated user', async () => {
    const { context } = contextFor(SampleController, 'adminOnly');

    expect((await errorFrom(() => guard.canActivate(context))).errorCode).toBe('AUTH-403');
  });
});
