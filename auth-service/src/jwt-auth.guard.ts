import { CanActivate, ExecutionContext, Injectable } from "@nestjs/common";
import { Request } from "express";
import { unauthorised } from "./auth-errors";
import { AccessClaims, TokenService } from "./token.service";

export interface AuthenticatedRequest extends Request {
  user: AccessClaims;
}

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(private readonly tokens: TokenService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const token = bearerToken(request.headers.authorization);
    if (!token) {
      throw unauthorised();
    }
    try {
      request.user = this.tokens.verifyAccessToken(token);
    } catch {
      throw unauthorised();
    }
    return true;
  }
}

function bearerToken(header: string | undefined): string | undefined {
  if (!header) {
    return undefined;
  }
  const parts = header.split(" ");
  // The auth scheme is case-insensitive (RFC 7235); anything but "Bearer <token>" is refused.
  if (parts.length !== 2 || parts[0].toLowerCase() !== "bearer" || !parts[1]) {
    return undefined;
  }
  return parts[1];
}
