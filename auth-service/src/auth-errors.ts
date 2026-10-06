import { UnauthorizedException } from "@nestjs/common";

// Same errorCode and message as the trading API's SecurityConfig. Every token
// failure gets exactly this body, so a caller cannot tell an expired token
// from a forged one.
export const AUTH_401_BODY = Object.freeze({
  errorCode: "AUTH-401",
  message: "Unauthorised or invalid token",
});

export function unauthorised(): UnauthorizedException {
  return new UnauthorizedException({ ...AUTH_401_BODY });
}
