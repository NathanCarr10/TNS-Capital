import { Module } from '@nestjs/common';
import { RefreshTokensService } from './refresh-tokens.service';
import { UsersService } from './users.service';

@Module({
  providers: [UsersService, RefreshTokensService],
  exports: [UsersService, RefreshTokensService],
})
export class UsersModule {}
