import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.hasValidAccessToken()) {
    return true;
  }

  // Remember where the user was going, so login can take them back there
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
