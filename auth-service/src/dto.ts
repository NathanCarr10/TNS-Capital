import { IsString, Length } from "class-validator";

export class LoginDto {
  // Limits from LoginRequest in contracts/auth-api.yaml.
  @IsString()
  @Length(1, 100)
  username!: string;

  @IsString()
  @Length(1, 256)
  password!: string;
}

export class RefreshDto {
  // Only the type is checked here: an empty or malformed token is the service's
  // job to refuse, with AUTH-401 rather than a validation error.
  @IsString()
  refreshToken!: string;
}
