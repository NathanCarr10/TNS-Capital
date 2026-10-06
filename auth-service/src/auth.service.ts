import { Injectable, UnauthorizedException } from "@nestjs/common";
import { randomUUID } from "crypto";
import { unauthorised } from "./auth-errors";
import { logAuthEvent } from "./logger";
import { RefreshTokenStore } from "./refresh-token.store";
import { RefreshClaims, TokenService } from "./token.service";
import { UserStore } from "./user.store";

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

@Injectable()
export class AuthService {
  constructor(
    private readonly users: UserStore,
    private readonly tokens: TokenService,
    private readonly refreshTokens: RefreshTokenStore,
  ) {}

  async login(username: string, password: string): Promise<TokenPair> {
    const user = await this.users.verifyCredentials(username, password);
    if (!user) {
      throw new UnauthorizedException({ errorCode: "AUTH-401", message: "Invalid username or password" });
    }
    logAuthEvent("login_success", username);
    return this.issueTokenPair(user.username, user.roles, randomUUID());
  }

  // Every refresh rotates: the presented token is spent and a new pair is
  // issued in the same session family.
  async refresh(refreshToken: string): Promise<TokenPair> {
    let claims: RefreshClaims;
    try {
      claims = this.tokens.verifyRefreshToken(refreshToken);
    } catch {
      throw unauthorised();
    }

    const result = this.refreshTokens.consume(refreshToken);
    if (result.status === "reused") {
      logAuthEvent("refresh_token_reuse_detected", result.username);
      throw unauthorised();
    }
    if (result.status !== "valid" || result.username !== claims.sub) {
      throw unauthorised();
    }

    // Roles come from the user record, not the old token, so a role change
    // takes effect at the next refresh.
    const user = await this.users.find(claims.sub);
    if (!user) {
      this.refreshTokens.revokeFamily(claims.fam);
      throw unauthorised();
    }
    logAuthEvent("refresh_success", user.username);
    return this.issueTokenPair(user.username, user.roles, claims.fam);
  }

  private issueTokenPair(username: string, roles: string[], familyId: string): TokenPair {
    const accessToken = this.tokens.issueAccessToken(username, roles);
    const { token: refreshToken, claims } = this.tokens.issueRefreshToken(username, familyId);
    this.refreshTokens.save(refreshToken, username, familyId, claims.exp);
    return { accessToken, refreshToken };
  }
}
