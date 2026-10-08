import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import type { AccessTokenPayload } from '../token.service';

/** The verified access-token claims JwtAuthGuard put on this request. */
export const CurrentUser = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext): AccessTokenPayload | undefined =>
    ctx.switchToHttp().getRequest<{ user?: AccessTokenPayload }>().user,
);
