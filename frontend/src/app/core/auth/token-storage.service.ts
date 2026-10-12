import { Injectable } from '@angular/core';

import { TokenPair } from './auth.models';

const ACCESS_TOKEN_KEY = 'tns.accessToken';
const REFRESH_TOKEN_KEY = 'tns.refreshToken';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  get accessToken(): string | null {
    return sessionStorage.getItem(ACCESS_TOKEN_KEY);
  }

  get refreshToken(): string | null {
    return sessionStorage.getItem(REFRESH_TOKEN_KEY);
  }

  setTokens(tokens: TokenPair): void {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    sessionStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  }

  clearTokens(): void {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  }
}
