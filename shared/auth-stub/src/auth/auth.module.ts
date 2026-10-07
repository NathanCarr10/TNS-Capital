import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { UsersModule } from '../users/users.module';
import { AuthController } from './auth.controller';

export const JWT_ISSUER = 'urn:tns-capital:auth-stub';

@Module({
  imports: [
    UsersModule,
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        // Shared with the trading API, which verifies tokens with the same secret
        secret: config.getOrThrow<string>('JWT_SECRET'),
        signOptions: { algorithm: 'HS256', expiresIn: '1h', issuer: JWT_ISSUER },
      }),
    }),
  ],
  controllers: [AuthController],
})
export class AuthModule {}
