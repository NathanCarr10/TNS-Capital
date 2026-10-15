import { PlatformErrorResponse } from '../../api/auth';

/** The platform's error envelope from a failed request, or null when the failure did not carry one. */
export function readApiError(error: unknown): PlatformErrorResponse | null {
  const body = (error as { error?: Partial<PlatformErrorResponse> } | null)?.error;
  return body && typeof body === 'object' && body.errorCode && body.message ? (body as PlatformErrorResponse) : null;
}

/** The API's message for a failed request, or the fallback when it sent none. */
export function readErrorMessage(error: unknown, fallback: string): string {
  return readApiError(error)?.message ?? fallback;
}
