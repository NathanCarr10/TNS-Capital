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

  it('posts registration details to the registration endpoint', () => {
    authService.register({ username: 'newtrader', password: 'password123', holderName: 'Ada Lovelace' }).subscribe();

    const request = httpMock.expectOne('/auth/register');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      username: 'newtrader',
      password: 'password123',
      holderName: 'Ada Lovelace',
    });
    request.flush({ username: 'newtrader', accountId: 'ACC-U00001' });
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

  it('recognises an admin from the roles claim', () => {
    sessionStorage.setItem('tns.accessToken', createJwtWithExpOffset(120, ['ADMIN']));

    expect(authService.isAdmin()).toBeTrue();
  });

  it('does not treat a trader as an admin', () => {
    sessionStorage.setItem('tns.accessToken', createJwtWithExpOffset(120, ['USER']));

    expect(authService.isAdmin()).toBeFalse();
  });

  it('does not treat a missing or unreadable token as an admin', () => {
    expect(authService.isAdmin()).toBeFalse();

    sessionStorage.setItem('tns.accessToken', 'not-a-jwt');

    expect(authService.isAdmin()).toBeFalse();
  });
});

function createJwtWithExpOffset(offsetInSeconds: number, roles?: string[]): string {
  const header = base64UrlEncode({ alg: 'HS256', typ: 'JWT' });
  const payload = base64UrlEncode({
    sub: 'admin',
    roles,
    exp: Math.floor(Date.now() / 1000) + offsetInSeconds,
  });

  return `${header}.${payload}.signature`;
}

function base64UrlEncode(value: object): string {
  const encoded = btoa(JSON.stringify(value));
  return encoded.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}
