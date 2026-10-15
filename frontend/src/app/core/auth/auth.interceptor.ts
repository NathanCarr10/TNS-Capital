import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';

// The auth endpoints answer 401 for bad credentials or tokens; refreshing would not help
const NO_REFRESH_PATHS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'];

function withToken(req: HttpRequest<unknown>, accessToken: string | null): HttpRequest<unknown> {
  return accessToken ? req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } }) : req;
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  return next(withToken(req, authService.getAccessToken())).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || NO_REFRESH_PATHS.some((path) => req.url.includes(path))) {
        return throwError(() => error);
      }
      if (!authService.canRefresh()) {
        authService.forceLogoutToLogin();
        return throwError(() => error);
      }

      // The access token has expired (15 minutes): get a new one and retry once
      return authService.refresh().pipe(
        catchError(() => {
          authService.forceLogoutToLogin();
          return throwError(() => error);
        }),
        switchMap(() =>
          next(withToken(req, authService.getAccessToken())).pipe(
            catchError((retryError: HttpErrorResponse) => {
              if (retryError.status === 401) {
                authService.forceLogoutToLogin();
              }
              return throwError(() => retryError);
            })
          )
        )
      );
    })
  );
};
