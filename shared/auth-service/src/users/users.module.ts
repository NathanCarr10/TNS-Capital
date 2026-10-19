import { Module } from '@nestjs/common';
import { StaffBootstrap } from './staff-bootstrap';
import { RefreshTokensService } from './refresh-tokens.service';
import { UsersService } from './users.service';

@Module({
  providers: [UsersService, RefreshTokensService, StaffBootstrap],
  exports: [UsersService, RefreshTokensService],
})
export class UsersModule {}
