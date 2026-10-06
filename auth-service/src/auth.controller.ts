import { Body, Controller, Get, HttpCode, Post, UseGuards } from "@nestjs/common";
import { AuthService, TokenPair } from "./auth.service";
import { Claims } from "./claims.decorator";
import { LoginDto, RefreshDto } from "./dto";
import { JwtAuthGuard } from "./jwt-auth.guard";
import { AccessClaims } from "./token.service";

@Controller("auth")
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post("login")
  @HttpCode(200)
  login(@Body() body: LoginDto): Promise<TokenPair> {
    return this.authService.login(body.username, body.password);
  }

  @Post("refresh")
  @HttpCode(200)
  refresh(@Body() body: RefreshDto): Promise<TokenPair> {
    return this.authService.refresh(body.refreshToken);
  }

  @Get("me")
  @UseGuards(JwtAuthGuard)
  me(@Claims() claims: AccessClaims): AccessClaims {
    return claims;
  }
}
