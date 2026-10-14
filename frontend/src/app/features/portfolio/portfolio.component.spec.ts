/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AnalyticsDashboardService } from '../../core/analytics/analytics-dashboard.service';
import { AuthService } from '../../core/auth/auth.service';
import { AccountStore } from '../../core/trading/account.store';
import { MarketDataService } from '../../core/trading/market-data.service';
import { ACCOUNT, tradingServiceStub } from '../../core/trading/testing/trading-fixtures';
import { TradingService } from '../../core/trading/trading.service';
import { PortfolioComponent } from './portfolio.component';

describe('PortfolioComponent', () => {
  let fixture: ComponentFixture<PortfolioComponent>;
  let tradingService: jasmine.SpyObj<TradingService>;
  let analyticsSpy: jasmine.SpyObj<AnalyticsDashboardService>;
  let isAdmin: boolean;

  const otherAccount = { ...ACCOUNT, id: 12, accountNumber: 'ACC-U000012', holderName: 'Trader Two' };

  const create = (): HTMLElement => {
    TestBed.configureTestingModule({
      imports: [PortfolioComponent],
      providers: [
        provideRouter([]),
        AccountStore,
        { provide: TradingService, useValue: tradingService },
        { provide: AuthService, useValue: { isAdmin: () => isAdmin } },
        { provide: AnalyticsDashboardService, useValue: analyticsSpy },
        { provide: MarketDataService, useValue: { getQuotes: () => of([]), getBars: () => of([]) } },
      ],
    });
    fixture = TestBed.createComponent(PortfolioComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  beforeEach(() => {
    tradingService = tradingServiceStub();
    analyticsSpy = jasmine.createSpyObj<AnalyticsDashboardService>('AnalyticsDashboardService', ['open']);
    isAdmin = false;
  });

  it('greets the trader and shows their account and cash', () => {
    const element = create();

    expect(element.querySelector('h1')?.textContent).toContain('Welcome, Trader');
    expect(element.querySelector('.account-number')?.textContent).toContain('ACC-U000011');
    expect(element.querySelector('.account-number .badge')?.textContent).toContain('ACTIVE');
    expect(element.querySelector('.cash strong')?.textContent).toContain('$5,000.00');
  });

  it('links to the order history and the trade page', () => {
    const element = create();

    const links = Array.from(element.querySelectorAll('.actions a')).map((link) => link.getAttribute('href'));
    expect(links).toEqual(['/orders', '/trade']);
  });

  it('does not show admin controls to a trader', () => {
    const element = create();

    expect(element.querySelector('#admin-account')).toBeNull();
    expect(tradingService.getAccounts).not.toHaveBeenCalled();
  });

  describe('for an admin', () => {
    beforeEach(() => {
      isAdmin = true;
      tradingService.getCurrentUserProfile.and.returnValue(of({ username: 'admin', roles: ['ADMIN'], accountId: null }));
      tradingService.getAccounts.and.returnValue(of([otherAccount, ACCOUNT]));
    });

    it('lists every account and shows the first one, read-only', () => {
      const element = create();

      const options = Array.from(element.querySelectorAll('#admin-account option')).map((option) =>
        option.textContent?.trim()
      );
      expect(options).toEqual(['ACC-U000011 - Trader One', 'ACC-U000012 - Trader Two']);
      expect(element.querySelector('.account-number')?.textContent).toContain('ACC-U000011');
      expect(element.textContent).toContain('Admin view: read-only');
      expect(element.querySelector('.place-order')).toBeNull();
    });

    it('switches to the chosen account', () => {
      tradingService.getAccount.and.returnValue(of(otherAccount));
      const element = create();

      const select = element.querySelector<HTMLSelectElement>('#admin-account')!;
      select.value = String(otherAccount.id);
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(tradingService.getAccount).toHaveBeenCalledWith(otherAccount.id);
      expect(element.querySelector('.account-number')?.textContent).toContain('ACC-U000012');
    });

    it('opens the analytics dashboard', () => {
      const element = create();

      Array.from(element.querySelectorAll('button'))
        .find((button) => button.textContent?.trim() === 'Analytics')!
        .click();

      expect(analyticsSpy.open).toHaveBeenCalled();
    });

    it('says so when there are no accounts', () => {
      tradingService.getAccounts.and.returnValue(of([]));

      const element = create();

      expect(element.querySelector('.note.error')?.textContent).toContain('There are no trading accounts yet.');
    });
  });
});
