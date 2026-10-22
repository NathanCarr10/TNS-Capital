/// <reference types="jasmine" />

import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;
  let queryParams: Record<string, string>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['login']);
    queryParams = {};

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { get queryParamMap() { return convertToParamMap(queryParams); } } },
        },
      ],
    }).compileComponents();

    // The template's routerLinks need the real router, so only its navigation is stubbed
    routerSpy = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    spyOn(routerSpy, 'navigate').and.resolveTo(true);
    spyOn(routerSpy, 'navigateByUrl').and.resolveTo(true);

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('submits valid credentials', () => {
    authServiceSpy.login.and.returnValue(of(undefined));

    component.loginForm.setValue({ email: 'admin@tns-capital.local', password: 'adminPassword' });
    component.onSubmit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      email: 'admin@tns-capital.local',
      password: 'adminPassword',
    });
  });

  it('shows API error for invalid password', () => {
    authServiceSpy.login.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: {
              errorCode: 'AUTH-401',
              message: 'invalid email or password',
              timestamp: new Date().toISOString(),
            },
          })
      )
    );

    component.loginForm.setValue({ email: 'admin@tns-capital.local', password: 'wrong-password' });
    component.onSubmit();

    expect(component.errorMessage()).toBe('invalid email or password');
  });

  it('sends traders to the portfolio page', () => {
    authServiceSpy.login.and.returnValue(of(undefined));

    component.loginForm.setValue({ email: 'trader@example.com', password: 'traderPassword' });
    component.onSubmit();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/portfolio']);
  });

  it('sends admins to the portfolio page too', () => {
    authServiceSpy.login.and.returnValue(of(undefined));

    component.loginForm.setValue({ email: 'admin@tns-capital.local', password: 'adminPassword' });
    component.onSubmit();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/portfolio']);
  });

  it('takes anyone back to the page they were going to, even an admin', () => {
    queryParams = { returnUrl: '/portfolio' };
    authServiceSpy.login.and.returnValue(of(undefined));

    component.loginForm.setValue({ email: 'admin@tns-capital.local', password: 'adminPassword' });
    component.onSubmit();

    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/portfolio');
  });

  for (const returnUrl of ['//evil.example', 'https://evil.example', '/login?returnUrl=/login']) {
    it(`ignores the return address ${returnUrl}`, () => {
      queryParams = { returnUrl };
      authServiceSpy.login.and.returnValue(of(undefined));
  
      component.loginForm.setValue({ email: 'trader@example.com', password: 'traderPassword' });
      component.onSubmit();

      expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/portfolio']);
    });
  }
});
