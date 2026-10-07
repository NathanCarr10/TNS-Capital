import { All, Body, Controller, Get, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { methodNotAllowed } from '../common/method-not-allowed';
import type { AuthenticatedUser, RegisteredUser } from '../users/users.service';
import { AuthService, LoginResult } from './auth.service';
import { CurrentUser } from './decorators/current-user.decorator';
import { Public } from './decorators/public.decorator';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';
import { RegisterDto } from './dto/register.dto';

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  /**
   * POST /auth/register - Register a new user and open their trading account
   *
   * The new account starts ACTIVE with a cash balance of 0. The response
   * carries its id, which the user passes to the trading API.
   *
   * Satisfies AC#5: Response never includes password or hash
   */
  @Public()
  @Post('register')
  @HttpCode(HttpStatus.CREATED)
  register(@Body() body: RegisterDto): Promise<RegisteredUser> {
    return this.authService.register(body.username, body.password, body.holderName);
  }

  /**
   * POST /auth/login - Authenticate user and issue an access and a refresh token
   *
   * Access token claims (contract): exactly sub, roles, iss, iat, exp
   * - sub: username; the trading API looks up the user's account by it
   * - roles: ["USER"] or ["ADMIN"], from users.role
   * - iss: urn:tns-capital:auth-stub
   * - exp: 1 hour after iat
   *
   * Algorithm: HS256, pinned in AuthModule, never read from a token.
   */
  @Public()
  @Post('login')
  @HttpCode(HttpStatus.OK)
  login(@Body() body: LoginDto): Promise<LoginResult> {
    return this.authService.login(body.username, body.password);
  }

  /** POST /auth/refresh - Exchange a refresh token for a new access token */
  @Public()
  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  refresh(@Body() body: RefreshDto): Promise<{ accessToken: string }> {
    return this.authService.refresh(body.refreshToken);
  }

  /** POST /auth/logout - Revoke a refresh token */
  @Public()
  @Post('logout')
  @HttpCode(HttpStatus.OK)
  logout(@Body() body: RefreshDto): Promise<{ loggedOut: true }> {
    return this.authService.logout(body.refreshToken);
  }

  /** GET /auth/me - The user the access token belongs to */
  @Get('me')
  me(@CurrentUser() user: AuthenticatedUser): Pick<AuthenticatedUser, 'username' | 'roles' | 'accountId'> {
    return { username: user.username, roles: user.roles, accountId: user.accountId };
  }

  // Declared after the real handlers, so only the wrong methods reach it
  @Public()
  @All(['register', 'login', 'refresh', 'logout', 'me'])
  methodNotAllowed(): never {
    return methodNotAllowed();
  }
}
