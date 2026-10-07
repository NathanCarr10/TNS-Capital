import { All, Body, Controller, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ApiException } from '../common/api.exception';
import { methodNotAllowed } from '../common/method-not-allowed';
import { UsernameTakenError, UsersService } from '../users/users.service';
import { LoginDto } from './dto/login.dto';
import { RegisterDto } from './dto/register.dto';

export interface RegisterResponse {
  id: number;
  username: string;
  createdAt: Date;
  accountId: number;
  accountNumber: string;
}

@Controller()
export class AuthController {
  constructor(
    private readonly users: UsersService,
    private readonly jwt: JwtService,
  ) {}

  /**
   * POST /register - Register a new user and open their trading account
   *
   * The new account starts ACTIVE with a cash balance of 0. The response
   * carries its id, which the user passes to the trading API.
   *
   * Satisfies AC#4: Duplicate username returns 409
   * Satisfies AC#5: Response never includes password or hash
   */
  @Post('register')
  @HttpCode(HttpStatus.CREATED)
  async register(@Body() body: RegisterDto): Promise<RegisterResponse> {
    try {
      const user = await this.users.register(body.username, body.password, body.holderName);
      return {
        id: user.id,
        username: user.username,
        createdAt: user.createdAt,
        accountId: user.accountId,
        accountNumber: user.accountNumber,
      };
    } catch (error) {
      if (error instanceof UsernameTakenError) {
        throw new ApiException(HttpStatus.CONFLICT, 'USR-409', 'User already exists');
      }
      throw error;
    }
  }

  @All('register')
  registerMethodNotAllowed(): never {
    return methodNotAllowed();
  }

  /**
   * POST /login - Authenticate user and issue JWT
   *
   * Token claims (contract): exactly sub, roles, iss, iat, exp
   * - sub: username; the trading API looks up the user's account by it
   * - roles: ["USER"] or ["ADMIN"], from users.role
   * - iss: urn:tns-capital:auth-stub
   * - exp: 1 hour after iat
   *
   * Algorithm: HS256, pinned in AuthModule, never read from a token.
   */
  @Post('login')
  @HttpCode(HttpStatus.OK)
  async login(@Body() body: LoginDto): Promise<{ token: string }> {
    const user = await this.users.authenticate(body.username, body.password);
    if (!user) {
      // Don't reveal whether the user exists or the password is wrong
      throw new ApiException(HttpStatus.UNAUTHORIZED, 'AUTH-401', 'invalid username or password');
    }

    const token = await this.jwt.signAsync({ sub: user.username, roles: user.roles });
    return { token };
  }

  @All('login')
  loginMethodNotAllowed(): never {
    return methodNotAllowed();
  }
}
