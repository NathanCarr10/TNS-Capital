/// <reference types="jasmine" />

import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { AccountStore } from '../../core/trading/account.store';
import { MarketDataService } from '../../core/trading/market-data.service';
import { ACCOUNT, tradingServiceStub } from '../../core/trading/testing/trading-fixtures';
import { TradingService } from '../../core/trading/trading.service';
import { PortfolioComponent } from './portfolio.component';

describe('PortfolioComponent', () => {
  let fixture: ComponentFixture<PortfolioComponent>;
  let tradingService: jasmine.SpyObj<TradingService>;
  let isSupport: boolean;
  let getQuotes: jasmine.Spy;

  const otherAccount = { ...ACCOUNT, id: 12, accountNumber: 'ACC-U000012', holderName: 'Trader Two' };

  const create = (): HTMLElement => {
    TestBed.configureTestingModule({
      imports: [PortfolioComponent],
      providers: [
        provideRouter([]),
        AccountStore,
        { provide: TradingService, useValue: tradingService },
        { provide: AuthService, useValue: { isSupport: () => isSupport } },
        { provide: MarketDataService, useValue: { getQuotes, getBars: () => of([]) } },
      ],
    });
    fixture = TestBed.createComponent(PortfolioComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  beforeEach(() => {
    tradingService = tradingServiceStub();
    isSupport = false;
    getQuotes = jasmine.createSpy('getQuotes').and.returnValue(of([]));
  });

  it('greets the trader and shows their account and cash', () => {
    const element = create();

    expect(element.querySelector('h1')?.textContent).toContain('Welcome, Trader');
    expect(element.querySelector('.account-number')?.textContent).toContain('ACC-U000011');
    expect(element.querySelector('.account-number .badge')?.textContent).toContain('ACTIVE');
    expect(element.querySelector('.cash strong')?.textContent).toContain('$5,000.00');
  });

  it('shows the cash left to trade and the cash held for open orders', () => {
    tradingService.getAccount.and.returnValue(of({ ...ACCOUNT, reservedCash: 1500, availableCash: 3500 }));
    const element = create();

    expect(element.querySelector('.cash strong')?.textContent).toContain('$5,000.00');
    expect(element.querySelector('.cash .available')?.textContent).toContain('$3,500.00 available to trade');
    expect(element.querySelector('.cash .reserved')?.textContent).toContain('$1,500.00 in open orders');
  });

  it('links to the order history and the trade page', () => {
    const element = create();

    const links = Array.from(element.querySelectorAll('.actions a')).map((link) => link.getAttribute('href'));
    expect(links).toEqual(['/orders', '/trade']);
  });

  it('shows a stored script payload in the holder name as text instead of running it', () => {
    const payload = '<img src=x onerror=alert(1)>';
    tradingService.getAccount.and.returnValue(of({ ...ACCOUNT, holderName: payload }));

    const element = create();
    const holder = element.querySelector('.holder')!;

    expect(holder.textContent).toContain(payload);
    expect(holder.innerHTML).toContain('&lt;img src=x onerror=alert(1)&gt;');
    expect(element.querySelector('img[src="x"]')).toBeNull();
  });

  it('does not show support controls to a trader', () => {
    const element = create();

    expect(element.querySelector('#support-account')).toBeNull();
    expect(element.querySelector('[role="switch"]')).toBeNull();
    expect(tradingService.getAccounts).not.toHaveBeenCalled();
  });

  describe('for support', () => {
    beforeEach(() => {
      isSupport = true;
      tradingService.getCurrentUserProfile.and.returnValue(of({ email: 'support@tns-capital.local', roles: ['SUPPORT'], accountId: null }));
      tradingService.getAccounts.and.returnValue(of([otherAccount, ACCOUNT]));
    });

    it('lists every account and shows the first one, read-only', () => {
      const element = create();

      const options = Array.from(element.querySelectorAll('#support-account option')).map((option) =>
        option.textContent?.trim()
      );
      expect(options).toEqual(['ACC-U000011 - Trader One', 'ACC-U000012 - Trader Two']);
      expect(element.querySelector('.account-number')?.textContent).toContain('ACC-U000011');
      expect(element.textContent).toContain("Support view: orders can only be placed by the account's owner.");
      expect(element.querySelector('.place-order')).toBeNull();
    });

    it("quotes the shown account's holdings, as there is no market watch to do it", fakeAsync(() => {
      tradingService.getAccountPositions.and.returnValue(
        of([{ accountId: ACCOUNT.id, symbol: 'AAPL', quantity: 10, reservedQuantity: 0, availableQuantity: 10, averageCost: 150, marketValue: null }])
      );
      const element = create();
      tick();

      expect(getQuotes).toHaveBeenCalledWith(['AAPL']);
      expect(element.querySelector('app-market-watch')).toBeNull();
      discardPeriodicTasks();
    }));

    it('switches to the chosen account', () => {
      tradingService.getAccount.and.returnValue(of(otherAccount));
      const element = create();

      const select = element.querySelector<HTMLSelectElement>('#support-account')!;
      select.value = String(otherAccount.id);
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(tradingService.getAccount).toHaveBeenCalledWith(otherAccount.id);
      expect(element.querySelector('.account-number')?.textContent).toContain('ACC-U000012');
    });

    it('suspends the active account and reactivates it', () => {
      const suspended = { ...ACCOUNT, status: 'SUSPENDED' as const };
      tradingService.updateAccountStatus.and.returnValue(of(suspended));
      const element = create();
      const toggle = element.querySelector<HTMLButtonElement>('[role="switch"]')!;
      expect(toggle.getAttribute('aria-checked')).toBe('true');

      toggle.click();
      fixture.detectChanges();

      expect(tradingService.updateAccountStatus).toHaveBeenCalledWith(ACCOUNT.id, { status: 'SUSPENDED' });
      expect(toggle.getAttribute('aria-checked')).toBe('false');
      expect(toggle.textContent).toContain('Suspended');
      expect(element.querySelector('.account-number .badge')?.textContent).toContain('SUSPENDED');

      tradingService.updateAccountStatus.and.returnValue(of(ACCOUNT));
      toggle.click();
      fixture.detectChanges();

      expect(tradingService.updateAccountStatus).toHaveBeenCalledWith(ACCOUNT.id, { status: 'ACTIVE' });
      expect(toggle.getAttribute('aria-checked')).toBe('true');
    });

    it("shows the API's reason when the status change is refused", () => {
      tradingService.updateAccountStatus.and.returnValue(
        throwError(() => ({ status: 403, error: { errorCode: 'ACC-403', message: 'The account is not in an active state for this operation' } }))
      );
      const element = create();

      element.querySelector<HTMLButtonElement>('[role="switch"]')!.click();
      fixture.detectChanges();

      expect(element.querySelector('.status-control [role="alert"]')?.textContent).toContain(
        'The account is not in an active state for this operation'
      );
      expect(element.querySelector('[role="switch"]')?.getAttribute('aria-checked')).toBe('true');
    });

    it('cannot reactivate a closed account', () => {
      tradingService.getAccounts.and.returnValue(of([{ ...ACCOUNT, status: 'CLOSED' as const }]));
      const element = create();

      expect(element.querySelector<HTMLButtonElement>('[role="switch"]')!.disabled).toBeTrue();
    });

    it('says so when there are no accounts', () => {
      tradingService.getAccounts.and.returnValue(of([]));

      const element = create();

      expect(element.querySelector('.note.error')?.textContent).toContain('There are no trading accounts yet.');
    });
  });

  describe('deposit', () => {
    const button = (name: string): HTMLButtonElement =>
      Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(
        (candidate) => candidate.textContent!.trim() === name
      )!;
    const depositAmount = (amount: number): void => {
      button('Deposit cash').click();
      fixture.detectChanges();
      const input: HTMLInputElement = fixture.nativeElement.querySelector('#deposit-amount');
      input.value = String(amount);
      input.dispatchEvent(new Event('input'));
      button('Confirm deposit').click();
      fixture.detectChanges();
    };

    it('deposits into the account and shows the new balance', () => {
      tradingService.deposit.and.returnValue(of({ ...ACCOUNT, cashBalance: ACCOUNT.cashBalance + 250 }));
      const element = create();

      depositAmount(250);

      expect(tradingService.deposit).toHaveBeenCalledWith(ACCOUNT.id, { amount: 250 });
      expect(element.querySelector('.deposit-form output')!.textContent).toContain('Cash deposited successfully.');
      expect(element.querySelector('.account-card')!.textContent).toContain(
        (ACCOUNT.cashBalance + 250).toLocaleString('en-US', { style: 'currency', currency: 'USD' })
      );
    });

    it('does not send an amount of zero', () => {
      const element = create();

      depositAmount(0);

      expect(tradingService.deposit).not.toHaveBeenCalled();
      expect(element.querySelector('.deposit-form [role="alert"]')!.textContent).toContain(
        'Enter an amount greater than $0.00.'
      );
    });

    it("shows the API's reason when the deposit is refused", () => {
      tradingService.deposit.and.returnValue(
        throwError(() => ({ status: 403, error: { errorCode: 'ACC-403', message: 'The account is not in an active state for this operation' } }))
      );
      const element = create();

      depositAmount(100);

      expect(element.querySelector('.deposit-form [role="alert"]')!.textContent).toContain(
        'The account is not in an active state for this operation'
      );
    });

    it('is not offered to support, who view accounts read-only', () => {
      isSupport = true;
      tradingService.getAccounts.and.returnValue(of([ACCOUNT]));
      create();

      expect(button('Deposit cash')).toBeUndefined();
    });
  });
});
