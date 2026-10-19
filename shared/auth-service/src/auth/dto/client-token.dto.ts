import { ApiProperty } from '@nestjs/swagger';
import { IsDefined, IsString, Length } from 'class-validator';

export const MISSING_CLIENT_CREDENTIALS = 'clientId and clientSecret are required';

/** Body of POST /auth/token (client credentials of a backend service) */
export class ClientTokenDto {
  @ApiProperty({ example: 'strategy-service' })
  @IsDefined({ message: MISSING_CLIENT_CREDENTIALS })
  @IsString()
  @Length(1, 100)
  clientId: string;

  @ApiProperty({ format: 'password' })
  @IsDefined({ message: MISSING_CLIENT_CREDENTIALS })
  @IsString()
  @Length(1, 256)
  clientSecret: string;
}
