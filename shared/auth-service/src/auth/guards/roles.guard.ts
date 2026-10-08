import { CanActivate, ExecutionContext, HttpStatus, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { ApiException } from '../../common/api.exception';
import type { Role } from '../../users/users.service';
import { ROLES_KEY } from '../decorators/roles.decorator';

/**
 * Enforces @Roles(). Runs after JwtAuthGuard (AuthModule registers them in
 * that order), so request.user already holds the verified claims; roles come
 * from the token, as in the trading API. Routes without @Roles() are open to
 * any authenticated user.
 */
@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const required = this.reflector.getAllAndOverride<Role[] | undefined>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (!required || required.length === 0) {
      return true;
    }

    const user = context.switchToHttp().getRequest<{ user?: { roles: Role[] } }>().user;
    if (!user?.roles.some((role) => required.includes(role))) {
      // Same code and message as the trading API
      throw new ApiException(HttpStatus.FORBIDDEN, 'AUTH-403', 'You do not have permission to access this resource');
    }
    return true;
  }
}
