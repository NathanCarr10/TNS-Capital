import { HttpException, HttpStatus } from '@nestjs/common';

/**
 * Platform error envelope: every error response is exactly
 * { errorCode, message, timestamp } with an ISO 8601 timestamp.
 */
export interface ErrorResponse {
  errorCode: string;
  message: string;
  timestamp: string;
}

export function errorResponse(errorCode: string, message: string): ErrorResponse {
  return { errorCode, message, timestamp: new Date().toISOString() };
}

/**
 * An HTTP error with its platform error code (e.g. "AUTH-401", "VAL-422").
 */
export class ApiException extends HttpException {
  constructor(
    status: HttpStatus,
    readonly errorCode: string,
    message: string,
  ) {
    super(message, status);
  }
}
