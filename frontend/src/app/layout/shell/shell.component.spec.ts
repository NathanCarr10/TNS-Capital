/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { ThemeService } from '../../core/theme/theme.service';
import { tradingServiceStub } from '../../core/trading/testing/trading-fixtures';
import { TradingService } from '../../core/trading/trading.service';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  let fixture: ComponentFixture<ShellComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let isAdmin: boolean;

  const create = (): HTMLElement => {
    authServiceSpy.isAdmin.and.returnValue(isAdmin);
    fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['logout', 'isAdmin']);
    authServiceSpy.logout.and.returnValue(of(undefined));
    isAdmin = false;
    const tradingService = tradingServiceStub();
    tradingService.getAccounts.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: TradingService, useValue: tradingService },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    // The theme lives on the document; leave it as the next spec expects
    if (TestBed.inject(ThemeService).theme() === 'dark') {
      TestBed.inject(ThemeService).toggle();
    }
  });

  it('links to Portfolio, Trade and Order History only', () => {
    const element = create();

    const links = Array.from(element.querySelectorAll('nav a')).map((link) => [
      link.textContent?.trim(),
      link.getAttribute('href'),
    ]);
    expect(links).toEqual([
      ['Portfolio', '/portfolio'],
      ['Trade', '/trade'],
      ['Order History', '/orders'],
    ]);
  });

  it("shows the account holder's name, initials and account number", () => {
    const element = create();

    expect(element.querySelector('.user-name')?.textContent).toContain('Trader One');
    expect(element.querySelector('.avatar')?.textContent).toContain('TO');
    expect(element.querySelector('.user-sub')?.textContent).toContain('ACC-U000011');
    expect(element.querySelector('.user .badge')?.textContent).toContain('Trader');
  });

  it('shows an admin by username', () => {
    isAdmin = true;

    const element = create();

    expect(element.querySelector('.user-name')?.textContent).toContain('trader1');
    expect(element.querySelector('.user .badge')?.textContent).toContain('Admin');
  });

  it('shows whether the NYSE is open', () => {
    const element = create();

    expect(element.querySelector('.market-status')?.textContent).toMatch(/NYSE (open|closed)/);
  });

  it('switches between light and dark mode', () => {
    const element = create();
    const before = document.documentElement.dataset['theme'];

    (element.querySelector('.icon-btn') as HTMLButtonElement).click();

    expect(document.documentElement.dataset['theme']).not.toBe(before);
  });

  it('logs out and returns to the login page', () => {
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    const element = create();

    (element.querySelector('.logout') as HTMLButtonElement).click();

    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
