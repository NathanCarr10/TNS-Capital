import { CanActivate, ExecutionContext, HttpStatus, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { Request } from 'express';
import { ApiException } from '../../common/api.exception';
import { IS_PUBLIC_KEY } from '../decorators/public.decorator';
import { AccessTokenPayload, INVALID_ACCESS_TOKEN, TokenService } from '../token.service';

/**
 * Applied to every route (AuthModule registers it globally); routes marked
 * @Public() skip it. Requires "Authorization: Bearer <access token>" and puts
 * the verified claims on request.user.
 *
 * Verification is local: signature, algorithm, issuer, expiry and token type
 * are checked with the shared secret, with no database lookup or network call,
 * the same check the trading API makes. A handler that needs the current user
 * record (GET /auth/me) loads it itself.
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly tokens: TokenService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (isPublic) {
      return true;
    }

    const request = context.switchToHttp().getRequest<Request & { user?: AccessTokenPayload }>();
    const token = bearerToken(request);
    if (!token) {
      throw unauthorised();
    }

    request.user = await this.tokens.verify(token);
    return true;
  }
}

// The scheme is case-insensitive (RFC 7235); anything but exactly "Bearer <token>" is refused
function bearerToken(request: Request): string | null {
  const parts = (request.headers.authorization ?? '').split(' ');
  return parts.length === 2 && parts[0].toLowerCase() === 'bearer' && parts[1] ? parts[1] : null;
}

function unauthorised(): ApiException {
  return new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_ACCESS_TOKEN);
}
