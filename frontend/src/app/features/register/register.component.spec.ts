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
        email: 'ada@example.com',
        createdAt: '2026-10-13T09:00:00Z',
        accountId: 1,
        accountNumber: 'ACC-U00001',
      })
    );

    component.registerForm.setValue({
      email: 'ada@example.com',
      password: 'password123',
      confirmPassword: 'password123',
      holderName: 'Ada Lovelace',
    });
    component.onSubmit();

    expect(authServiceSpy.register).toHaveBeenCalledWith({
      email: 'ada@example.com',
      password: 'password123',
      holderName: 'Ada Lovelace',
    });
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('lower-cases the email before sending it', () => {
    authServiceSpy.register.and.returnValue(
      of({ id: 1, email: 'ada@example.com', createdAt: '2026-10-13T09:00:00Z', accountId: 1, accountNumber: 'ACC-U00001' })
    );

    component.registerForm.setValue({
      email: 'Ada@Example.com',
      password: 'password123',
      confirmPassword: 'password123',
      holderName: '',
    });
    component.onSubmit();

    expect(authServiceSpy.register).toHaveBeenCalledWith({ email: 'ada@example.com', password: 'password123' });
  });

  it('has no username field', () => {
    expect((fixture.nativeElement as HTMLElement).querySelector('#username')).toBeNull();
    expect(Object.keys(component.registerForm.controls)).not.toContain('username');
  });

  it('rejects mismatched passwords without calling the API', () => {
    component.registerForm.setValue({
      email: 'ada@example.com',
      password: 'password123',
      confirmPassword: 'different123',
      holderName: '',
    });
    component.onSubmit();

    expect(authServiceSpy.register).not.toHaveBeenCalled();
    expect(component.errorMessage()).toBe('Passwords do not match.');
  });

  describe('rejects script input', () => {
    const payloads = ['<script>alert(1)</script>', '<img src=x onerror=alert(1)>', '"><svg onload=alert(1)>', 'javascript:alert(1)'];
    const fill = (overrides: Partial<{ email: string; holderName: string }>) =>
      component.registerForm.setValue({
        email: 'ada@example.com',
        password: 'password123',
        confirmPassword: 'password123',
        holderName: 'Ada Lovelace',
        ...overrides,
      });
    const text = (): string => (fixture.nativeElement as HTMLElement).textContent ?? '';

    payloads.forEach((payload) => {
      it(`in the email: ${payload}`, () => {
        fill({ email: payload });
        component.onSubmit();
        fixture.detectChanges();

        expect(component.registerForm.controls.email.hasError('email')).toBeTrue();
        expect(authServiceSpy.register).not.toHaveBeenCalled();
        expect(text()).toContain('Enter a valid email address and passwords with at least 8 characters.');
      });

      it(`in the account holder name: ${payload}`, () => {
        fill({ holderName: payload });
        component.onSubmit();
        fixture.detectChanges();

        expect(component.registerForm.controls.holderName.hasError('pattern')).toBeTrue();
        expect(authServiceSpy.register).not.toHaveBeenCalled();
        expect(text()).toContain("Account holder name can only contain letters, numbers, spaces and . , ' -");
      });
    });

    it('still accepts real names with accents, apostrophes and hyphens', () => {
      fill({ email: 'zoe.oneill_1@example.com', holderName: "Zoë O'Neill-Smith" });

      expect(component.registerForm.valid).toBeTrue();
    });
  });
});
