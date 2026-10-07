import { HttpStatus, ValidationError, ValidationPipe } from '@nestjs/common';
import { ApiException } from './api.exception';

export const MISSING_CREDENTIALS = 'missing username or password';

/**
 * Validates request bodies against their DTOs. Unknown properties are
 * rejected (the contract's additionalProperties: false) and every failure is
 * a 400 VAL-422 with a single readable message.
 */
export function createValidationPipe(): ValidationPipe {
  return new ValidationPipe({
    whitelist: true,
    forbidNonWhitelisted: true,
    exceptionFactory: (errors) => new ApiException(HttpStatus.BAD_REQUEST, 'VAL-422', describe(errors)),
  });
}

function describe(errors: ValidationError[]): string {
  const unexpected = errors.filter((e) => e.constraints?.whitelistValidation).map((e) => e.property);
  if (unexpected.length > 0) {
    return `Unexpected property: ${unexpected.join(', ')}`;
  }
  if (errors.some((e) => e.constraints?.isDefined)) {
    return MISSING_CREDENTIALS;
  }
  const first = errors.flatMap((e) => Object.values(e.constraints ?? {}))[0];
  return first ?? 'Request body is invalid';
}
