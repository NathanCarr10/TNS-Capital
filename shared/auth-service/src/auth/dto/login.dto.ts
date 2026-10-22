import { ApiProperty } from '@nestjs/swagger';
import { IsDefined, IsEmail, IsString, Length } from 'class-validator';
import { MISSING_CREDENTIALS } from '../../common/validation';

/** Contract: LoginRequest (contracts/auth-api.yaml) */
export class LoginDto {
  @ApiProperty({ example: 'trader1@example.com', format: 'email', maxLength: 320 })
  @IsDefined({ message: MISSING_CREDENTIALS })
  @IsEmail()
  @Length(1, 320)
  email: string;

  @ApiProperty({ example: 'password123', format: 'password' })
  @IsDefined({ message: MISSING_CREDENTIALS })
  @IsString()
  @Length(1, 256)
  password: string;
}
