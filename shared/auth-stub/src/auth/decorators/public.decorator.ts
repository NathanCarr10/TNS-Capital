import { SetMetadata } from '@nestjs/common';

export const IS_PUBLIC_KEY = 'isPublic';

/** Lets a route through JwtAuthGuard without an access token. Every other route needs one. */
export const Public = () => SetMetadata(IS_PUBLIC_KEY, true);
