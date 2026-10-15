import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { provideApiClients } from '../api/api.providers';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('tns.accessToken', 'old-access');
    sessionStorage.setItem('tns.refreshToken', 'old-refresh');

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideApiClients(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  it('sends the access token', () => {
    http.get('/api/v1/accounts').subscribe();

    expect(httpMock.expectOne('/api/v1/accounts').request.headers.get('Authorization')).toBe('Bearer old-access');
  });

  it('refreshes an expired access token and retries the request with the new one', () => {
    let body: unknown;
    http.get('/api/v1/accounts').subscribe((response) => (body = response));

    httpMock.expectOne('/api/v1/accounts').flush(null, { status: 401, statusText: 'Unauthorized' });
    const refresh = httpMock.expectOne('/auth/refresh');
    expect(refresh.request.body).toEqual({ refreshToken: 'old-refresh' });
    refresh.flush({ accessToken: 'new-access', refreshToken: 'new-refresh' });

    const retry = httpMock.expectOne('/api/v1/accounts');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-access');
    retry.flush([{ id: 1 }]);

    expect(body).toEqual([{ id: 1 }]);
    expect(sessionStorage.getItem('tns.refreshToken')).toBe('new-refresh');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('sends one refresh for requests that fail together, since a refresh token is single use', () => {
    http.get('/api/v1/accounts').subscribe();
    http.get('/api/v1/instruments').subscribe();

    httpMock.expectOne('/api/v1/accounts').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/api/v1/instruments').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/auth/refresh').flush({ accessToken: 'new-access', refreshToken: 'new-refresh' });

    httpMock.expectOne('/api/v1/accounts').flush([]);
    httpMock.expectOne('/api/v1/instruments').flush([]);
  });

  it('logs out when the refresh is refused', () => {
    let failed = false;
    http.get('/api/v1/accounts').subscribe({ error: () => (failed = true) });

    httpMock.expectOne('/api/v1/accounts').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBeTrue();
    expect(sessionStorage.getItem('tns.accessToken')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not refresh when the login itself is refused', () => {
    http.post('/auth/login', {}).subscribe({ error: () => undefined });

    httpMock.expectOne('/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone('/auth/refresh');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
