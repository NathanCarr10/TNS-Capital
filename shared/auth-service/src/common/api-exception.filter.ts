import { ArgumentsHost, Catch, ExceptionFilter, HttpException, HttpStatus, Logger } from '@nestjs/common';
import type { Request, Response } from 'express';
import { ApiException, errorResponse } from './api.exception';

const MALFORMED_BODY = 'Request body is missing or malformed';

/**
 * Turns every error into the platform envelope. Never exposes stack traces or
 * framework messages; unexpected errors are logged server-side and returned
 * as SYS-500.
 */
@Catch()
export class ApiExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(ApiExceptionFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const request = ctx.getRequest<Request>();
    const response = ctx.getResponse<Response>();

    const [status, errorCode, message] = this.describe(exception, request);
    response.status(status).json(errorResponse(errorCode, message));
  }

  private describe(exception: unknown, request: Request): [number, string, string] {
    if (exception instanceof ApiException) {
      return [exception.getStatus(), exception.errorCode, exception.message];
    }
    if (isBodyParseError(exception)) {
      return [HttpStatus.BAD_REQUEST, 'VAL-422', MALFORMED_BODY];
    }
    if (exception instanceof HttpException) {
      switch (exception.getStatus()) {
        case HttpStatus.BAD_REQUEST:
          return [HttpStatus.BAD_REQUEST, 'VAL-422', MALFORMED_BODY];
        case HttpStatus.NOT_FOUND:
          return [HttpStatus.NOT_FOUND, 'NOT-404', `Resource not found: ${request.path}`];
        case HttpStatus.METHOD_NOT_ALLOWED:
          return [HttpStatus.METHOD_NOT_ALLOWED, 'REQ-405', `HTTP method ${request.method} is not supported here`];
      }
    }
    this.logger.error('Unexpected error', exception instanceof Error ? exception.stack : String(exception));
    return [HttpStatus.INTERNAL_SERVER_ERROR, 'SYS-500', 'Internal server error'];
  }
}

// body-parser rejects invalid JSON with a SyntaxError tagged "entity.parse.failed"
function isBodyParseError(exception: unknown): boolean {
  return (exception as { type?: string } | null)?.type === 'entity.parse.failed';
}
