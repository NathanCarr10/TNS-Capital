/**
 * JwtAuthGuard and RolesGuard on their own, with hand-built execution
 * contexts. The HTTP tests (auth-api.spec.ts) run JwtAuthGuard end to end;
 * no route uses @Roles() yet, so RolesGuard is only exercised here.
 */
import { Controller, ExecutionContext, Get } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { AuthService } from '../src/auth/auth.service';
import { Public } from '../src/auth/decorators/public.decorator';
import { Roles } from '../src/auth/decorators/roles.decorator';
import { JwtAuthGuard } from '../src/auth/guards/jwt-auth.guard';
import { RolesGuard } from '../src/auth/guards/roles.guard';
import { AccessTokenPayload, TokenService } from '../src/auth/token.service';
import { ApiException } from '../src/common/api.exception';
import { AuthenticatedUser } from '../src/users/users.service';

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
  const payload: AccessTokenPayload = { sub: 'trader', roles: ['USER'], iss: 'x', iat: 0, exp: 0 };
  const tokens = { verify: jest.fn() };
  const auth = { validate: jest.fn() };
  const guard = new JwtAuthGuard(new Reflector(), tokens as unknown as TokenService, auth as unknown as AuthService);

  beforeEach(() => {
    jest.resetAllMocks();
    tokens.verify.mockResolvedValue(payload);
    auth.validate.mockResolvedValue(TRADER);
  });

  it.each([
    ['a handler', SampleController, 'open'],
    ['a controller', PublicController, 'handler'],
  ])('lets a route through without a token when %s is @Public()', async (_case, controller, handler) => {
    const { context } = contextFor(controller, handler);

    expect(await guard.canActivate(context)).toBe(true);
    expect(tokens.verify).not.toHaveBeenCalled();
  });

  it('verifies the bearer token and puts the user on the request', async () => {
    const { context, request } = contextFor(SampleController, 'anyUser', {
      headers: { authorization: 'Bearer abc.def.ghi' },
    });

    expect(await guard.canActivate(context)).toBe(true);
    expect(tokens.verify).toHaveBeenCalledWith('abc.def.ghi');
    expect(auth.validate).toHaveBeenCalledWith(payload);
    expect(request.user).toEqual(TRADER);
  });

  it.each([
    ['no Authorization header', {}],
    ['a non-Bearer scheme', { authorization: 'Basic abc' }],
    ['a Bearer scheme with no token', { authorization: 'Bearer' }],
  ])('rejects %s with AUTH-401 without verifying anything', async (_case, headers) => {
    const { context } = contextFor(SampleController, 'anyUser', { headers });

    const error = await errorFrom(guard.canActivate(context));
    expect(error.getStatus()).toBe(401);
    expect(error.errorCode).toBe('AUTH-401');
    expect(tokens.verify).not.toHaveBeenCalled();
  });

  it('passes on the error when the token fails verification', async () => {
    const invalid = new ApiException(401, 'AUTH-401', 'Unauthorised or invalid token');
    tokens.verify.mockRejectedValue(invalid);
    const { context } = contextFor(SampleController, 'anyUser', { headers: { authorization: 'Bearer bad' } });

    expect(await errorFrom(guard.canActivate(context))).toBe(invalid);
    expect(auth.validate).not.toHaveBeenCalled();
  });

  it('rejects a valid token whose user no longer exists', async () => {
    auth.validate.mockResolvedValue(null);
    const { context, request } = contextFor(SampleController, 'anyUser', {
      headers: { authorization: 'Bearer abc.def.ghi' },
    });

    const error = await errorFrom(guard.canActivate(context));
    expect(error.errorCode).toBe('AUTH-401');
    expect(request.user).toBeUndefined();
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
