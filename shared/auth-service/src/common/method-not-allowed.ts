import { HttpException, HttpStatus } from '@nestjs/common';

/**
 * Thrown by catch-all @All() handlers declared after a path's real handlers,
 * so a known path called with the wrong method gets 405 instead of 404.
 */
export function methodNotAllowed(): never {
  throw new HttpException('Method Not Allowed', HttpStatus.METHOD_NOT_ALLOWED);
}
