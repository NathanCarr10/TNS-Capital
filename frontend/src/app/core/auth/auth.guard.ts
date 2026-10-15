import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.hasValidAccessToken()) {
    return true;
  }

  // Remember where the user was going, so login can take them back there
  const toLogin = router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });

  // The access token has expired, but the session may still be alive: refresh it
  if (authService.canRefresh()) {
    return authService.refresh().pipe(
      map(() => true),
      catchError(() => of(toLogin))
    );
  }
  return toLogin;
};
