import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AnalyticsDashboardService } from '../analytics/analytics-dashboard.service';
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

/**
 * Admins work in the analytics dashboard, not this app: traders' accounts are support's
 * (docs/DECISIONS.md, "Roles"). An admin who signs in here is sent there instead.
 */
export const notAdminGuard: CanActivateFn = () => {
  if (inject(AuthService).isAdmin()) {
    inject(AnalyticsDashboardService).open();
    return false;
  }
  return true;
};

/** The support inbox is only available to staff with the SUPPORT role. */
export const supportGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  return authService.isSupport() ? true : inject(Router).createUrlTree(['/portfolio']);
};
