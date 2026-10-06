import { UnauthorizedException } from "@nestjs/common";

// Same errorCode and message as the trading API's SecurityConfig. Every token
// failure gets exactly this code and message, so a caller cannot tell an
// expired token from a forged one.
export const AUTH_401 = Object.freeze({
  errorCode: "AUTH-401",
  message: "Unauthorised or invalid token",
});

export function unauthorised(message: string = AUTH_401.message): UnauthorizedException {
  return new UnauthorizedException({ errorCode: AUTH_401.errorCode, message });
}
