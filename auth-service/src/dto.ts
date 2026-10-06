import { IsNotEmpty, IsString } from "class-validator";

export class LoginDto {
  @IsString()
  @IsNotEmpty()
  username!: string;

  @IsString()
  @IsNotEmpty()
  password!: string;
}

export class RefreshDto {
  // Only the type is checked here: an empty or malformed token is the service's
  // job to refuse, with AUTH-401 rather than a validation error.
  @IsString()
  refreshToken!: string;
}
