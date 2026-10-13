import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { provideApiClients } from '../api/api.providers';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let authService: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideApiClients()],
    });

    authService = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  it('stores token pair on successful login', () => {
    authService.login({ username: 'admin', password: 'adminPassword' }).subscribe();

    const request = httpMock.expectOne('/auth/login');
    expect(request.request.method).toBe('POST');
    request.flush({
      accessToken: createJwtWithExpOffset(900),
      refreshToken: 'refresh-token',
    });

    expect(sessionStorage.getItem('tns.accessToken')).toBeTruthy();
    expect(sessionStorage.getItem('tns.refreshToken')).toBe('refresh-token');
  });

  it('clears session and calls logout endpoint when refresh token exists', () => {
    sessionStorage.setItem('tns.accessToken', createJwtWithExpOffset(900));
    sessionStorage.setItem('tns.refreshToken', 'refresh-token');

    authService.logout().subscribe();

    const request = httpMock.expectOne('/auth/logout');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ refreshToken: 'refresh-token' });
    request.flush({ loggedOut: true });

    expect(sessionStorage.getItem('tns.accessToken')).toBeNull();
    expect(sessionStorage.getItem('tns.refreshToken')).toBeNull();
  });

  it('returns false when token is expired', () => {
    sessionStorage.setItem('tns.accessToken', createJwtWithExpOffset(-1));

    expect(authService.hasValidAccessToken()).toBeFalse();
  });

  it('returns true when token is not expired', () => {
    sessionStorage.setItem('tns.accessToken', createJwtWithExpOffset(120));

    expect(authService.hasValidAccessToken()).toBeTrue();
  });
});

function createJwtWithExpOffset(offsetInSeconds: number): string {
  const header = base64UrlEncode({ alg: 'HS256', typ: 'JWT' });
  const payload = base64UrlEncode({
    sub: 'admin',
    exp: Math.floor(Date.now() / 1000) + offsetInSeconds,
  });

  return `${header}.${payload}.signature`;
}

function base64UrlEncode(value: object): string {
  const encoded = btoa(JSON.stringify(value));
  return encoded.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}
