/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
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
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: ActivatedRoute, useValue: {} },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('submits valid registration details and returns to login', () => {
    authServiceSpy.register.and.returnValue(of({ username: 'newtrader', accountId: 'ACC-U00001' }));

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