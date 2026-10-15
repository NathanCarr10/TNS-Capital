/**
 * Allow-list patterns for free-text fields users type in. Anything outside the
 * list (e.g. < > " / = ( ) used by script payloads) is rejected before it is
 * sent. Keep in step with shared/auth-service/src/auth/dto/register.dto.ts and
 * the trading API's account DTOs, which enforce the same rules server side.
 */

/** Letters, digits, dot, underscore and hyphen, e.g. trader_1 */
export const USERNAME_PATTERN = /^[A-Za-z0-9._-]+$/;

/** Letters in any language, digits, spaces and . , ' - e.g. Ada O'Neill-Lovelace */
export const HOLDER_NAME_PATTERN = /^[\p{L}\p{M}\p{N} .,'-]+$/u;
