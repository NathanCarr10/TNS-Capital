import { IsDefined, IsOptional, IsString, Length, Matches } from 'class-validator';

export class RegisterDto {
  @IsDefined()
  @IsString()
  @Length(3, 100)
  username: string;

  @IsDefined()
  @IsString()
  @Length(8, 256)
  password: string;

  /** Name on the trading account; defaults to the username */
  @IsOptional()
  @IsString()
  @Length(1, 255)
  @Matches(/\S/, { message: 'holderName must not be blank' })
  holderName?: string;
}
