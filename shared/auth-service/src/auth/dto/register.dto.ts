import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsDefined, IsEmail, IsOptional, IsString, Length, Matches } from 'class-validator';
import { MISSING_CREDENTIALS } from '../../common/validation';

/**
 * Allow-list, so characters used by script payloads (< > " / = ( )) are
 * rejected. Keep in step with frontend/src/app/core/validation/input-patterns.ts.
 */
export const HOLDER_NAME_PATTERN = /^[\p{L}\p{M}\p{N} .,'-]+$/u;

export class RegisterDto {
  @ApiProperty({ example: 'trader1@example.com', format: 'email', maxLength: 320 })
  @IsDefined({ message: MISSING_CREDENTIALS })
  @IsEmail()
  @Length(1, 320)
  email: string;

  @ApiProperty({ example: 'password123', format: 'password', minLength: 8 })
  @IsDefined({ message: MISSING_CREDENTIALS })
  @IsString()
  @Length(8, 256)
  password: string;

  /** Name on the trading account; defaults to the part of the email before the "@" */
  @ApiPropertyOptional({
    example: 'Ada Lovelace',
    description: 'Name on the trading account; defaults to the part of the email before the "@"',
    pattern: HOLDER_NAME_PATTERN.source,
  })
  @IsOptional()
  @IsString()
  @Length(1, 255)
  @Matches(/\S/, { message: 'holderName must not be blank' })
  @Matches(HOLDER_NAME_PATTERN, { message: "holderName can only contain letters, numbers, spaces and . , ' -" })
  holderName?: string;
}
