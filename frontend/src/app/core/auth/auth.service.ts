import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

import {
  JwtClaims,
  LoginRequest,
  LogoutResponse,
  PlatformErrorResponse,
  RefreshRequest,
  TokenPair,
} from './auth.models';
import { TokenStorageService } from './token-storage.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokenStorage = inject(TokenStorageService);

  private readonly authState$ = new BehaviorSubject<boolean>(this.hasValidAccessToken());

  readonly isAuthenticated$ = this.authState$.asObservable();

  login(credentials: LoginRequest): Observable<void> {
    return this.http.post<TokenPair>('/auth/login', credentials).pipe(
      tap((tokens) => {
        this.tokenStorage.setTokens(tokens);
        this.authState$.next(true);
      }),
      map(() => undefined)
    );
  }

  logout(): Observable<void> {
    const refreshToken = this.tokenStorage.refreshToken;
    this.clearSession();

    if (!refreshToken) {
      return of(undefined);
    }

    const request: RefreshRequest = { refreshToken };

    return this.http.post<LogoutResponse>('/auth/logout', request).pipe(
      map(() => undefined),
      catchError(() => of(undefined))
    );
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
        .replace(/-/g, '+')
        .replace(/_/g, '/');
      const normalizedPayload = payload.padEnd(Math.ceil(payload.length / 4) * 4, '=');
      const claimsJson = atob(normalizedPayload);
      return JSON.parse(claimsJson) as JwtClaims;
    } catch {
      return {};
    }
  }

  static getErrorMessage(error: unknown): string {
    const defaultMessage = 'Invalid username or password.';

    if (!error || typeof error !== 'object') {
      return defaultMessage;
    }

    const httpError = error as { error?: PlatformErrorResponse };
    return httpError.error?.message ?? defaultMessage;
  }
}
