import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { catchError, finalize, map, shareReplay, tap } from 'rxjs/operators';

import {
  AccessTokenClaims,
  AuthenticationService,
  LoginRequest,
  PlatformErrorResponse,
  RegisterRequest,
  RegisterResponse,
} from '../../api/auth';
import { TokenStorageService } from './token-storage.service';

// Decoding can fail or meet a token without every claim, so all are optional here
type JwtClaims = Partial<AccessTokenClaims>;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authApi = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly tokenStorage = inject(TokenStorageService);

  private readonly authState$ = new BehaviorSubject<boolean>(this.hasValidAccessToken());

  /** The refresh in progress, shared by every caller until it settles. */
  private refreshInFlight: Observable<void> | null = null;

  readonly isAuthenticated$ = this.authState$.asObservable();

  login(credentials: LoginRequest): Observable<void> {
    return this.authApi.login(credentials).pipe(
      tap((tokens) => {
        this.tokenStorage.setTokens(tokens);
        this.authState$.next(true);
      }),
      map(() => undefined)
    );
  }

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.authApi.register(request);
  }

  logout(): Observable<void> {
    const refreshToken = this.tokenStorage.refreshToken;
    this.clearSession();

    if (!refreshToken) {
      return of(undefined);
    }

    return this.authApi.logout({ refreshToken }).pipe(
      map(() => undefined),
      catchError(() => of(undefined))
    );
  }

  /**
   * Swaps the refresh token for a new access and refresh token pair.
   *
   * Refresh tokens are single use and the auth service revokes the whole session when one is
   * presented twice, so concurrent callers (several requests getting a 401 at once) share one
   * request instead of each sending the same token.
   */
  refresh(): Observable<void> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }
    const refreshToken = this.tokenStorage.refreshToken;
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }

    this.refreshInFlight = this.authApi.refresh({ refreshToken }).pipe(
      tap((tokens) => {
        this.tokenStorage.setTokens(tokens);
        this.authState$.next(true);
      }),
      map(() => undefined),
      finalize(() => (this.refreshInFlight = null)),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    return this.refreshInFlight;
  }

  /** Whether a refresh is worth trying: there is a refresh token to send. */
  canRefresh(): boolean {
    return !!this.tokenStorage.refreshToken;
  }

  forceLogoutToLogin(): void {
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  getAccessToken(): string | null {
    return this.tokenStorage.accessToken;
  }

  hasValidAccessToken(): boolean {
    const token = this.tokenStorage.accessToken;
    if (!token) {
      return false;
    }

    const claims = this.decodeClaims(token);
    if (!claims.exp) {
      return false;
    }

    const nowInSeconds = Math.floor(Date.now() / 1000);
    return claims.exp > nowInSeconds;
  }

  /** True when the stored access token carries the ADMIN role: the analytics dashboard's users. */
  isAdmin(): boolean {
    return this.hasRole('ADMIN');
  }

  /** True when the stored access token carries the SUPPORT role: staff who look after traders' accounts here. */
  isSupport(): boolean {
    return this.hasRole('SUPPORT');
  }

  private hasRole(role: AccessTokenClaims.RolesEnum): boolean {
    const token = this.tokenStorage.accessToken;
    return token ? (this.decodeClaims(token).roles ?? []).includes(role) : false;
  }

  private clearSession(): void {
    this.tokenStorage.clearTokens();
    this.authState$.next(false);
  }

  private decodeClaims(token: string): JwtClaims {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return {};
    }

    try {
      const payload = parts[1]
        .replaceAll('-', '+')
        .replaceAll('_', '/');
      const normalizedPayload = payload.padEnd(Math.ceil(payload.length / 4) * 4, '=');
      const claimsJson = atob(normalizedPayload);
      return JSON.parse(claimsJson) as JwtClaims;
    } catch {
      return {};
    }
  }

  static getErrorMessage(error: unknown, defaultMessage = 'Invalid username or password.'): string {

    if (!error || typeof error !== 'object') {
      return defaultMessage;
    }

    const httpError = error as { error?: PlatformErrorResponse };
    return httpError.error?.message ?? defaultMessage;
  }
}
