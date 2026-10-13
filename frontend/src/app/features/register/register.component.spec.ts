/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let component: RegisterComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['register']);

    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        provideRouter([]),
      ],
    }).compileComponents();

    // The template's routerLink needs the real router, so only its navigation is stubbed
    routerSpy = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    spyOn(routerSpy, 'navigate').and.resolveTo(true);

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('submits valid registration details and returns to login', () => {
    authServiceSpy.register.and.returnValue(
      of({
        id: 1,
        username: 'newtrader',
        createdAt: '2026-10-13T09:00:00Z',
        accountId: 1,
        accountNumber: 'ACC-U00001',
      })
    );

    component.registerForm.setValue({
      username: 'newtrader',
      password: 'password123',
      confirmPassword: 'password123',
      holderName: 'Ada Lovelace',
    });
    component.onSubmit();

    expect(authServiceSpy.register).toHaveBeenCalledWith({
      username: 'newtrader',
      password: 'password123',
      holderName: 'Ada Lovelace',
    });
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('rejects mismatched passwords without calling the API', () => {
    component.registerForm.setValue({
      username: 'newtrader',
      password: 'password123',
      confirmPassword: 'different123',
      holderName: '',
    });
    component.onSubmit();

    expect(authServiceSpy.register).not.toHaveBeenCalled();
    expect(component.errorMessage()).toBe('Passwords do not match.');
  });
});