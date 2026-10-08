import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsDefined, IsOptional, IsString, Length, Matches } from 'class-validator';
import { MISSING_CREDENTIALS } from '../../common/validation';

export class RegisterDto {
  @ApiProperty({ example: 'trader1', minLength: 3, maxLength: 100 })
  @IsDefined({ message: MISSING_CREDENTIALS })
  @IsString()
  @Length(3, 100)
  username: string;

  @ApiProperty({ example: 'password123', format: 'password', minLength: 8 })
  @IsDefined({ message: MISSING_CREDENTIALS })
  @IsString()
  @Length(8, 256)
  password: string;

  /** Name on the trading account; defaults to the username */
  @ApiPropertyOptional({ example: 'Ada Lovelace', description: 'Name on the trading account; defaults to the username' })
  @IsOptional()
  @IsString()
  @Length(1, 255)
  @Matches(/\S/, { message: 'holderName must not be blank' })
  holderName?: string;
}
