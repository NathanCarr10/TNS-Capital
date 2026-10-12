/// <reference types="jasmine" />

import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['login']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigate']) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('submits valid credentials', () => {
    authServiceSpy.login.and.returnValue(of(undefined));

    component.loginForm.setValue({ username: 'admin', password: 'adminPassword' });
    component.onSubmit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      username: 'admin',
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
              message: 'invalid username or password',
              timestamp: new Date().toISOString(),
            },
          })
      )
    );

    component.loginForm.setValue({ username: 'admin', password: 'wrong-password' });
    component.onSubmit();

    expect(component.errorMessage()).toBe('invalid username or password');
  });
});
