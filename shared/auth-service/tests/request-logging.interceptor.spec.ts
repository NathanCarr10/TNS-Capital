/**
 * RequestLoggingInterceptor with a hand-built execution context: one log line
 * per request at the right level, and never the body.
 */
import { CallHandler, ExecutionContext, HttpStatus, Logger } from '@nestjs/common';
import { lastValueFrom, of, throwError } from 'rxjs';
import { ApiException } from '../src/common/api.exception';
import { RequestLoggingInterceptor } from '../src/common/request-logging.interceptor';

function context(request: object, statusCode = 200): ExecutionContext {
  return {
    switchToHttp: () => ({ getRequest: () => request, getResponse: () => ({ statusCode }) }),
  } as unknown as ExecutionContext;
}

const handler = (result: () => ReturnType<CallHandler['handle']>): CallHandler => ({ handle: result });

describe('RequestLoggingInterceptor', () => {
  const interceptor = new RequestLoggingInterceptor();
  let log: jest.SpyInstance;
  let warn: jest.SpyInstance;
  let error: jest.SpyInstance;

  beforeEach(() => {
    log = jest.spyOn(Logger.prototype, 'log').mockImplementation();
    warn = jest.spyOn(Logger.prototype, 'warn').mockImplementation();
    error = jest.spyOn(Logger.prototype, 'error').mockImplementation();
  });

  afterEach(() => jest.restoreAllMocks());

  it('logs method, path, status, time and the user of a successful request', async () => {
    const request = { method: 'GET', path: '/auth/me', user: { sub: 'trader@example.com' } };

    await lastValueFrom(interceptor.intercept(context(request), handler(() => of({ ok: true }))));

    expect(log).toHaveBeenCalledWith(expect.stringMatching(/^GET \/auth\/me 200 \d+ms user=trader@example.com$/));
  });

  it('logs a rejected login as a warning, without the password', async () => {
    const request = { method: 'POST', path: '/auth/login', body: { email: 'x@example.com', password: 'secret123' } };
    const failure = new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', 'invalid email or password');

    await expect(
      lastValueFrom(interceptor.intercept(context(request), handler(() => throwError(() => failure)))),
    ).rejects.toBe(failure);

    expect(warn).toHaveBeenCalledWith(expect.stringMatching(/^POST \/auth\/login 401 \d+ms$/));
    expect(JSON.stringify([log.mock.calls, warn.mock.calls])).not.toContain('secret123');
  });

  it('logs an unexpected error as 500 at error level', async () => {
    const request = { method: 'POST', path: '/auth/register' };

    await expect(
      lastValueFrom(interceptor.intercept(context(request), handler(() => throwError(() => new Error('db down'))))),
    ).rejects.toThrow('db down');

    expect(error).toHaveBeenCalledWith(expect.stringMatching(/^POST \/auth\/register 500 \d+ms$/));
  });
});
