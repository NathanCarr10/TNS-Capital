import { CanActivate, ExecutionContext, HttpStatus, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { Request } from 'express';
import { ApiException } from '../../common/api.exception';
import type { AuthenticatedUser } from '../../users/users.service';
import { AuthService } from '../auth.service';
import { IS_PUBLIC_KEY } from '../decorators/public.decorator';
import { INVALID_ACCESS_TOKEN, TokenService } from '../token.service';

/**
 * Applied to every route (AuthModule registers it globally); routes marked
 * @Public() skip it. Requires "Authorization: Bearer <access token>" and puts
 * the authenticated user on request.user.
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly tokens: TokenService,
    private readonly auth: AuthService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (isPublic) {
      return true;
    }

    const request = context.switchToHttp().getRequest<Request & { user?: AuthenticatedUser }>();
    const token = bearerToken(request);
    if (!token) {
      throw unauthorised();
    }

    const user = await this.auth.validate(await this.tokens.verify(token));
    if (!user) {
      // Validly signed, but the user has since been deleted
      throw unauthorised();
    }
    request.user = user;
    return true;
  }
}

function bearerToken(request: Request): string | null {
  const [scheme, token] = (request.headers.authorization ?? '').split(' ');
  return scheme?.toLowerCase() === 'bearer' && token ? token : null;
}

function unauthorised(): ApiException {
  return new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', INVALID_ACCESS_TOKEN);
}
