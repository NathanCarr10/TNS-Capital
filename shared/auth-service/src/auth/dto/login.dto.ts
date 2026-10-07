import { IsDefined, IsString, Length } from 'class-validator';
import { MISSING_CREDENTIALS } from '../../common/validation';

/** Contract: LoginRequest (contracts/auth-api.yaml) */
export class LoginDto {
  @IsDefined({ message: MISSING_CREDENTIALS })
  @IsString()
  @Length(1, 100)
  username: string;

  @IsDefined({ message: MISSING_CREDENTIALS })
  @IsString()
  @Length(1, 256)
  password: string;
}
