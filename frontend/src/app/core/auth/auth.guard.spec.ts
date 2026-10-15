import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';

import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', [
      'hasValidAccessToken',
      'canRefresh',
      'refresh',
    ]);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);
    routerSpy.createUrlTree.and.returnValue({} as ReturnType<Router['createUrlTree']>);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('allows navigation with a valid token', () => {
    authServiceSpy.hasValidAccessToken.and.returnValue(true);

    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

    expect(result).toBeTrue();
  });

  it('redirects to login when there is no session to refresh', () => {
    authServiceSpy.hasValidAccessToken.and.returnValue(false);
    authServiceSpy.canRefresh.and.returnValue(false);

    TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/portfolio' } as never));

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/portfolio' },
    });
  });

  it('refreshes an expired access token and lets the navigation through', async () => {
    authServiceSpy.hasValidAccessToken.and.returnValue(false);
    authServiceSpy.canRefresh.and.returnValue(true);
    authServiceSpy.refresh.and.returnValue(of(undefined));

    const result = TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/portfolio' } as never));

    expect(await firstValueFrom(result as Observable<boolean | UrlTree>)).toBeTrue();
  });

  it('redirects to login when the refresh fails', async () => {
    const loginTree = {} as UrlTree;
    routerSpy.createUrlTree.and.returnValue(loginTree);
    authServiceSpy.hasValidAccessToken.and.returnValue(false);
    authServiceSpy.canRefresh.and.returnValue(true);
    authServiceSpy.refresh.and.returnValue(throwError(() => new Error('revoked')));

    const result = TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/orders' } as never));

    expect(await firstValueFrom(result as Observable<boolean | UrlTree>)).toBe(loginTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/orders' } });
  });
});
