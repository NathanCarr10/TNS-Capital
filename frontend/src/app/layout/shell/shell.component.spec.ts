/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';

import { DeadLetterQueueControllerService } from '../../api/trading';
import { AuthService } from '../../core/auth/auth.service';
import { StrategyLabService } from '../../core/strategy/strategy-lab.service';
import { ThemeService } from '../../core/theme/theme.service';
import { MarketDataService } from '../../core/trading/market-data.service';
import { tradingServiceStub } from '../../core/trading/testing/trading-fixtures';
import { TradingService } from '../../core/trading/trading.service';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  let fixture: ComponentFixture<ShellComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let isSupport: boolean;
  let strategyLab: jasmine.SpyObj<StrategyLabService>;
  let dlqApi: jasmine.SpyObj<DeadLetterQueueControllerService>;

  const create = (): HTMLElement => {
    authServiceSpy.isSupport.and.returnValue(isSupport);
    fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['logout', 'isSupport']);
    authServiceSpy.logout.and.returnValue(of(undefined));
    isSupport = false;
    const tradingService = tradingServiceStub();
    tradingService.getAccounts.and.returnValue(of([]));
    strategyLab = jasmine.createSpyObj<StrategyLabService>('StrategyLabService', ['activity']);
    strategyLab.activity.and.returnValue(of([]));
    dlqApi = jasmine.createSpyObj<DeadLetterQueueControllerService>('DeadLetterQueueControllerService', ['getDLQStatistics']);
    dlqApi.getDLQStatistics.and.returnValue(of({ pendingMessages: 0 }) as never);
    // The ticker tape quotes prices on every page
    const marketData = jasmine.createSpyObj<MarketDataService>('MarketDataService', ['getQuotes', 'getBars']);
    marketData.getQuotes.and.returnValue(of([]));
    marketData.getBars.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: TradingService, useValue: tradingService },
        { provide: StrategyLabService, useValue: strategyLab },
        { provide: DeadLetterQueueControllerService, useValue: dlqApi },
        { provide: MarketDataService, useValue: marketData },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    // The theme lives on the document; leave it as the next spec expects
    if (TestBed.inject(ThemeService).theme() === 'dark') {
      TestBed.inject(ThemeService).toggle();
    }
  });

  const navLinks = (element: HTMLElement): (string | null | undefined)[][] =>
    Array.from(element.querySelectorAll('nav a')).map((link) => [link.textContent?.trim(), link.getAttribute('href')]);

  it('links to Portfolio, Trade, Order History, Strategy Lab, Newsletter and Settings', () => {
    const element = create();

    expect(navLinks(element)).toEqual([
      ['Portfolio', '/portfolio'],
      ['Trade', '/trade'],
      ['Order History', '/orders'],
      ['Strategy Lab', '/strategies'],
      ['Market Movers', '/market-movers'],
      ['Settings', '/settings'],
    ]);
  });

  it('gives support Dead Letters and Support Inbox instead of Trade and Settings, and no symbol search', () => {
    isSupport = true;

    const element = create();

    expect(navLinks(element)).toEqual([
      ['Portfolio', '/portfolio'],
      ['Order History', '/orders'],
      ['Strategy Lab', '/strategies'],
      ['Market Movers', '/market-movers'],
      ['Dead Letters', '/dead-letters'],
      ['Support Inbox', '/support'],
    ]);
    expect(element.querySelector('app-symbol-search')).toBeNull();
  });

  it('shows support how many dead letters are pending', () => {
    isSupport = true;
    dlqApi.getDLQStatistics.and.returnValue(of({ pendingMessages: 3 }) as never);

    const element = create();
    const badge = element.querySelector('nav a[href="/dead-letters"] .nav-notification');

    expect(badge?.textContent).toContain('3');
    expect(badge?.getAttribute('aria-label')).toBe('3 pending dead letters');
  });

  it('shows no dead-letter badge when none are pending', () => {
    isSupport = true;

    expect(create().querySelector('nav a[href="/dead-letters"] .nav-notification')).toBeNull();
  });

  it("does not ask a trader's session for dead-letter counts", () => {
    create();

    expect(dlqApi.getDLQStatistics).not.toHaveBeenCalled();
  });

  it("shows the account holder's name, initials and account number", () => {
    const element = create();

    expect(element.querySelector('.user-name')?.textContent).toContain('Trader One');
    expect(element.querySelector('.avatar')?.textContent).toContain('TO');
    expect(element.querySelector('.user-sub')?.textContent).toContain('ACC-U000011');
    expect(element.querySelector('.user .badge')?.textContent).toContain('Trader');
  });

  it('shows support by email', () => {
    isSupport = true;

    const element = create();

    expect(element.querySelector('.user-name')?.textContent).toContain('ada@example.com');
    expect(element.querySelector('.user .badge')?.textContent).toContain('Support');
  });

  it('gives traders no Support Inbox link', () => {
    const element = create();

    expect(navLinks(element).map(([label]) => label)).not.toContain('Support Inbox');
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
