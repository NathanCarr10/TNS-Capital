import { Module } from '@nestjs/common';
import { AdminBootstrap } from './admin-bootstrap';
import { RefreshTokensService } from './refresh-tokens.service';
import { UsersService } from './users.service';

@Module({
  providers: [UsersService, RefreshTokensService, AdminBootstrap],
  exports: [UsersService, RefreshTokensService],
})
export class UsersModule {}
