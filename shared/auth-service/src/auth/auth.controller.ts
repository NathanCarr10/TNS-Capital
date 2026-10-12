import { All, Body, Controller, Get, HttpCode, HttpStatus, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiExcludeEndpoint, ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { ThrottlerGuard } from '@nestjs/throttler';
import { methodNotAllowed } from '../common/method-not-allowed';
import type { AuthenticatedUser, RegisteredUser } from '../users/users.service';
import { AuthService, LoginResult } from './auth.service';
import { CurrentUser } from './decorators/current-user.decorator';
import { Public } from './decorators/public.decorator';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';
import { RegisterDto } from './dto/register.dto';
import type { AccessTokenPayload } from './token.service';

@ApiTags('auth')
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
  @UseGuards(ThrottlerGuard)
  @ApiResponse({ status: 429, description: 'Too many attempts from this client; retry after Retry-After seconds' })
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Register a user and open their trading account' })
  @ApiResponse({ status: 201, description: 'User registered; the body carries the new accountId' })
  @ApiResponse({ status: 409, description: 'Username already taken' })
  register(@Body() body: RegisterDto): Promise<RegisteredUser> {
    return this.authService.register(body.username, body.password, body.holderName);
  }

  /**
   * POST /auth/login - Authenticate user and issue an access and a refresh token
   *
   * Access token claims: exactly sub, roles, typ, jti, iss, iat, exp
   * - sub: username; the trading API looks up the user's account by it
   * - roles: ["USER"] or ["ADMIN"], from users.role
   * - typ: "access"
   * - jti: a random UUID
   * - iss: urn:tns-capital:auth-service
   * - exp: 15 minutes after iat
   *
   * Algorithm: HS256, pinned in AuthModule, never read from a token.
   */
  @Public()
  @Post('login')
  @UseGuards(ThrottlerGuard)
  @ApiResponse({ status: 429, description: 'Too many attempts from this client; retry after Retry-After seconds' })
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Log in and receive an access token and a refresh token' })
  @ApiResponse({ status: 200, description: 'accessToken (15 min) and refreshToken (7 days)' })
  @ApiResponse({ status: 401, description: 'Invalid username or password' })
  login(@Body() body: LoginDto): Promise<LoginResult> {
    return this.authService.login(body.username, body.password);
  }

  /** POST /auth/refresh - Spend a refresh token for a new access token and refresh token */
  @Public()
  @Post('refresh')
  @UseGuards(ThrottlerGuard)
  @ApiResponse({ status: 429, description: 'Too many attempts from this client; retry after Retry-After seconds' })
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Spend a refresh token for a new token pair' })
  @ApiResponse({ status: 200, description: 'A new accessToken and refreshToken' })
  @ApiResponse({ status: 401, description: 'Refresh token unknown, expired or already used' })
  refresh(@Body() body: RefreshDto): Promise<LoginResult> {
    return this.authService.refresh(body.refreshToken);
  }

  /** POST /auth/logout - Revoke the session a refresh token belongs to */
  @Public()
  @Post('logout')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Revoke the login session a refresh token belongs to' })
  logout(@Body() body: RefreshDto): Promise<{ loggedOut: true }> {
    return this.authService.logout(body.refreshToken);
  }

  /** GET /auth/me - The user the access token belongs to. JwtAuthGuard has verified the claims. */
  @Get('me')
  @ApiBearerAuth()
  @ApiOperation({ summary: 'The user the access token belongs to' })
  @ApiResponse({ status: 401, description: 'Missing, invalid or expired access token' })
  async me(
    @CurrentUser() claims: AccessTokenPayload,
  ): Promise<Pick<AuthenticatedUser, 'username' | 'roles' | 'accountId'>> {
    const user = await this.authService.me(claims);
    return { username: user.username, roles: user.roles, accountId: user.accountId };
  }

  // Declared after the real handlers, so only the wrong methods reach it
  @Public()
  @All(['register', 'login', 'refresh', 'logout', 'me'])
  @ApiExcludeEndpoint()
  methodNotAllowed(): never {
    return methodNotAllowed();
  }
}
