import { Inject, Injectable } from "@nestjs/common";
import { randomUUID } from "crypto";
import * as jwt from "jsonwebtoken";
import { ACCESS_TOKEN_TTL_SECONDS, AUTH_CONFIG, AuthConfig, REFRESH_TOKEN_TTL_SECONDS } from "./config";

export interface AccessClaims {
  sub: string;
  roles: string[];
  typ: "access";
  iss: string;
  jti: string;
  iat: number;
  exp: number;
}

export interface RefreshClaims {
  sub: string;
  typ: "refresh";
  // Session family: every token rotated from one login shares it, so reuse
  // of any old token can revoke the whole session.
  fam: string;
  iss: string;
  jti: string;
  iat: number;
  exp: number;
}

export class InvalidTokenError extends Error {}

@Injectable()
export class TokenService {
  constructor(@Inject(AUTH_CONFIG) private readonly config: AuthConfig) {}

  issueAccessToken(username: string, roles: string[]): string {
    return jwt.sign({ roles, typ: "access" }, this.config.accessSecret, {
      algorithm: "HS256",
      expiresIn: ACCESS_TOKEN_TTL_SECONDS,
      issuer: this.config.issuer,
      subject: username,
      jwtid: randomUUID(),
    });
  }

  issueRefreshToken(username: string, familyId: string): { token: string; claims: RefreshClaims } {
    const token = jwt.sign({ typ: "refresh", fam: familyId }, this.config.refreshSecret, {
      algorithm: "HS256",
      expiresIn: REFRESH_TOKEN_TTL_SECONDS,
      issuer: this.config.issuer,
      subject: username,
      jwtid: randomUUID(),
    });
    return { token, claims: jwt.decode(token) as RefreshClaims };
  }

  // Local check only: signature, expiry, issuer and token type. No network
  // call and no database lookup, so the guard works even if the store is down.
  verifyAccessToken(token: string): AccessClaims {
    const claims = this.verify(token, this.config.accessSecret);
    if (claims.typ !== "access" || !isStringArray(claims.roles)) {
      throw new InvalidTokenError("not an access token");
    }
    return claims as unknown as AccessClaims;
  }

  verifyRefreshToken(token: string): RefreshClaims {
    const claims = this.verify(token, this.config.refreshSecret);
    if (claims.typ !== "refresh" || typeof claims.fam !== "string") {
      throw new InvalidTokenError("not a refresh token");
    }
    return claims as unknown as RefreshClaims;
  }

  private verify(token: string, secret: string): jwt.JwtPayload {
    let payload: string | jwt.JwtPayload;
    try {
      // Pinning the algorithm stops "alg: none" and algorithm-confusion tokens.
      payload = jwt.verify(token, secret, { algorithms: ["HS256"], issuer: this.config.issuer });
    } catch (e) {
      throw new InvalidTokenError((e as Error).message);
    }
    if (
      typeof payload !== "object" ||
      typeof payload.sub !== "string" ||
      typeof payload.jti !== "string" ||
      typeof payload.exp !== "number"
    ) {
      throw new InvalidTokenError("missing required claims");
    }
    return payload;
  }
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((v) => typeof v === "string");
}
