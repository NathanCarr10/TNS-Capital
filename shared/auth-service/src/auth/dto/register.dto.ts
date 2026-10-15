import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsDefined, IsOptional, IsString, Length, Matches } from 'class-validator';
import { MISSING_CREDENTIALS } from '../../common/validation';

/**
 * Allow-lists, so characters used by script payloads (< > " / = ( )) are
 * rejected. Keep in step with frontend/src/app/core/validation/input-patterns.ts.
 */
export const USERNAME_PATTERN = /^[A-Za-z0-9._-]+$/;
export const HOLDER_NAME_PATTERN = /^[\p{L}\p{M}\p{N} .,'-]+$/u;

export class RegisterDto {
  @ApiProperty({ example: 'trader1', minLength: 3, maxLength: 100, pattern: USERNAME_PATTERN.source })
  @IsDefined({ message: MISSING_CREDENTIALS })
  @IsString()
  @Length(3, 100)
  @Matches(USERNAME_PATTERN, { message: 'username can only contain letters, numbers, dots, underscores and hyphens' })
  username: string;

  @ApiProperty({ example: 'password123', format: 'password', minLength: 8 })
  @IsDefined({ message: MISSING_CREDENTIALS })
  @IsString()
  @Length(8, 256)
  password: string;

  /** Name on the trading account; defaults to the username */
  @ApiPropertyOptional({
    example: 'Ada Lovelace',
    description: 'Name on the trading account; defaults to the username',
    pattern: HOLDER_NAME_PATTERN.source,
  })
  @IsOptional()
  @IsString()
  @Length(1, 255)
  @Matches(/\S/, { message: 'holderName must not be blank' })
  @Matches(HOLDER_NAME_PATTERN, { message: "holderName can only contain letters, numbers, spaces and . , ' -" })
  holderName?: string;
}
