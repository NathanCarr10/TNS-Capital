import { CallHandler, ExecutionContext, HttpException, Injectable, Logger, NestInterceptor } from '@nestjs/common';
import type { Request, Response } from 'express';
import { Observable, tap } from 'rxjs';
import type { AccessTokenPayload } from '../auth/token.service';

/**
 * Logs one line per request: method, path, status, time taken and, once
 * JwtAuthGuard has verified a token, the token's subject (the user's email).
 *
 * Bodies and headers are never logged, so passwords and tokens stay out of
 * the logs. Failed credential checks (401 on /auth/login) are logged as
 * warnings, which makes password guessing visible.
 */
@Injectable()
export class RequestLoggingInterceptor implements NestInterceptor {
  private readonly logger = new Logger('HTTP');

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const http = context.switchToHttp();
    const request = http.getRequest<Request & { user?: AccessTokenPayload }>();
    const response = http.getResponse<Response>();
    const started = Date.now();

    return next.handle().pipe(
      tap({
        next: () => this.log(request, response.statusCode, started),
        error: (error: unknown) =>
          this.log(request, error instanceof HttpException ? error.getStatus() : 500, started),
      }),
    );
  }

  private log(request: Request & { user?: AccessTokenPayload }, status: number, started: number): void {
    const user = request.user?.sub ? ` user=${request.user.sub}` : '';
    const line = `${request.method} ${request.path} ${status} ${Date.now() - started}ms${user}`;
    if (status >= 500) {
      this.logger.error(line);
    } else if (status >= 400) {
      this.logger.warn(line);
    } else {
      this.logger.log(line);
    }
  }
}
