import { IsDefined, IsString, Length } from 'class-validator';

export const MISSING_REFRESH_TOKEN = 'missing refresh token';

/** Body of POST /auth/refresh and POST /auth/logout */
export class RefreshDto {
  @IsDefined({ message: MISSING_REFRESH_TOKEN })
  @IsString()
  @Length(1, 512)
  refreshToken: string;
}
