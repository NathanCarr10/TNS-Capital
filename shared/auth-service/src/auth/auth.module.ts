import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { APP_GUARD } from '@nestjs/core';
import { JwtModule } from '@nestjs/jwt';
import { ThrottlerModule } from '@nestjs/throttler';
import { UsersModule } from '../users/users.module';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { RolesGuard } from './guards/roles.guard';
import { DEFAULT_AUTH_RATE_LIMIT } from './rate-limit';
import { ACCESS_TOKEN_TTL_SECONDS, JWT_ISSUER, TokenService } from './token.service';

@Module({
  imports: [
    UsersModule,
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        // Shared with the trading API, which verifies tokens with the same secret
        secret: config.getOrThrow<string>('JWT_SECRET'),
        signOptions: { algorithm: 'HS256', expiresIn: ACCESS_TOKEN_TTL_SECONDS, issuer: JWT_ISSUER },
      }),
    }),
    // Brute-force protection for the credential endpoints (applied per route
    // with ThrottlerGuard): AUTH_RATE_LIMIT requests per client IP per minute
    ThrottlerModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => [
        { ttl: 60_000, limit: Number(config.get('AUTH_RATE_LIMIT') ?? DEFAULT_AUTH_RATE_LIMIT) },
      ],
    }),
  ],
  controllers: [AuthController],
  providers: [
    AuthService,
    TokenService,
    // Global guards run in this order: authenticate, then check roles.
    // Every route needs an access token unless marked @Public().
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: RolesGuard },
  ],
})
export class AuthModule {}
