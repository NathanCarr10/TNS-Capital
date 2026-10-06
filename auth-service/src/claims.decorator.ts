import { createParamDecorator, ExecutionContext } from "@nestjs/common";
import { AuthenticatedRequest } from "./jwt-auth.guard";

// The verified claims JwtAuthGuard attached to the request. Only use on routes behind the guard.
export const Claims = createParamDecorator(
  (_data: unknown, context: ExecutionContext) => context.switchToHttp().getRequest<AuthenticatedRequest>().user,
);
