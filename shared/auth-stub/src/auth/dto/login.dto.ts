import { IsDefined, IsString, Length } from 'class-validator';

/** Contract: LoginRequest (contracts/auth-api.yaml) */
export class LoginDto {
  @IsDefined()
  @IsString()
  @Length(1, 100)
  username: string;

  @IsDefined()
  @IsString()
  @Length(1, 256)
  password: string;
}
