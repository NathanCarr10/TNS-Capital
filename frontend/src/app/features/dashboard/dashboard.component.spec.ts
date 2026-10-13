/// <reference types="jasmine" />

import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AnalyticsDashboardService } from '../../core/analytics/analytics-dashboard.service';
import { AuthService } from '../../core/auth/auth.service';
import { DashboardComponent } from './dashboard.component';
import { MarketDataService } from './market-data.service';
import { TradingService } from './trading.service';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let tradingServiceSpy: jasmine.SpyObj<TradingService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let analyticsSpy: jasmine.SpyObj<AnalyticsDashboardService>;

  const account = {
    id: 11,
    accountNumber: 'ACC-U000011',
    holderName: 'Trader One',
    cashBalance: 5000,
    status: 'ACTIVE',
    lastUpdated: new Date().toISOString(),
  };

  beforeEach(async () => {
    tradingServiceSpy = jasmine.createSpyObj<TradingService>('TradingService', [
      'getCurrentUserProfile',
      'getAccounts',
      'getAccount',
      'getInstruments',
      'getAccountOrders',
      'getAccountPositions',
      'getAccountOrderHistory',
      'placeOrder',
      'getOrder',
    ]);
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['logout', 'isAdmin']);
    analyticsSpy = jasmine.createSpyObj<AnalyticsDashboardService>('AnalyticsDashboardService', ['open']);

    tradingServiceSpy.getCurrentUserProfile.and.returnValue(
      of({ username: 'myuser', roles: ['USER'], accountId: account.id })
    );
    tradingServiceSpy.getAccount.and.returnValue(of(account));
    tradingServiceSpy.getInstruments.and.returnValue(
      of([
        {
          symbol: 'AAPL',
          name: 'Apple Inc.',
          assetClass: 'EQUITY',
          currency: 'USD',
          tradable: true,
        },
      ])
    );
    tradingServiceSpy.getAccountPositions.and.returnValue(of([]));
    tradingServiceSpy.getAccountOrders.and.returnValue(
      of([
        {
          id: 'ord-history-1',
          accountId: account.id,
          symbol: 'AAPL',
          side: 'BUY',
          quantity: 10,
          price: 150,
          status: 'FILLED',
          createdOn: new Date('2026-01-01T10:00:00Z').toISOString(),
        },
      ])
    );
    tradingServiceSpy.getAccountOrderHistory.and.returnValue(of([]));
    authServiceSpy.logout.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: TradingService, useValue: tradingServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
        { provide: AnalyticsDashboardService, useValue: analyticsSpy },
        { provide: MarketDataService, useValue: { getQuotes: () => of([]) } },
        { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigate']) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('submits a successful BUY order and shows FILLED message', fakeAsync(() => {
    tradingServiceSpy.placeOrder.and.returnValue(of({ orderId: 'ord-buy', status: 'ACCEPTED' }));
    tradingServiceSpy.getOrder.and.returnValue(
      of({
        id: 'ord-buy',
        accountId: account.id,
        symbol: 'AAPL',
        side: 'BUY',
        quantity: 10,
        price: 150,
        status: 'FILLED',
        createdOn: new Date().toISOString(),
      })
    );

    component.orderForm.setValue({ symbol: 'AAPL', side: 'BUY', quantity: 10, price: 150 });
    component.onSubmit();
    tick();

    expect(tradingServiceSpy.placeOrder).toHaveBeenCalled();
    expect(component.successMessage()).toContain('FILLED');
    expect(component.errorMessage()).toBeNull();
    // The fill moves cash and holdings, so both are reloaded for the performance panel
    expect(tradingServiceSpy.getAccount).toHaveBeenCalledTimes(2);
    expect(tradingServiceSpy.getAccountPositions).toHaveBeenCalledTimes(2);
  }));

  it('submits a successful SELL order and shows FILLED message', fakeAsync(() => {
    tradingServiceSpy.placeOrder.and.returnValue(of({ orderId: 'ord-sell', status: 'ACCEPTED' }));
    tradingServiceSpy.getOrder.and.returnValue(
      of({
        id: 'ord-sell',
        accountId: account.id,
        symbol: 'AAPL',
        side: 'SELL',
        quantity: 5,
        price: 155,
        status: 'FILLED',
        createdOn: new Date().toISOString(),
      })
    );

    component.orderForm.setValue({ symbol: 'AAPL', side: 'SELL', quantity: 5, price: 155 });
    component.onSubmit();
    tick();

    expect(tradingServiceSpy.placeOrder).toHaveBeenCalledWith(
      jasmine.objectContaining({ side: 'SELL', quantity: 5 })
    );
    expect(component.successMessage()).toContain('FILLED');
  }));

  it('enforces quantity validation', () => {
    component.orderForm.setValue({ symbol: 'AAPL', side: 'BUY', quantity: 0, price: 100 });

    component.onSubmit();

    expect(component.orderForm.controls.quantity.invalid).toBeTrue();
    expect(tradingServiceSpy.placeOrder).not.toHaveBeenCalled();
  });

  it('enforces price validation when provided', () => {
    component.orderForm.setValue({ symbol: 'AAPL', side: 'BUY', quantity: 10, price: 0 });

    component.onSubmit();

    expect(component.orderForm.controls.price.invalid).toBeTrue();
    expect(tradingServiceSpy.placeOrder).not.toHaveBeenCalled();
  });

  it('shows rejected error message for insufficient funds', fakeAsync(() => {
    tradingServiceSpy.placeOrder.and.returnValue(of({ orderId: 'ord-rej', status: 'ACCEPTED' }));
    tradingServiceSpy.getOrder.and.returnValue(
      of({
        id: 'ord-rej',
        accountId: account.id,
        symbol: 'AAPL',
        side: 'BUY',
        quantity: 1000,
        price: 150,
        status: 'REJECTED',
        createdOn: new Date().toISOString(),
        statusReason: 'Insufficient funds for account 11',
      })
    );

    component.orderForm.setValue({ symbol: 'AAPL', side: 'BUY', quantity: 1000, price: 150 });
    component.onSubmit();
    tick();

    expect(component.errorMessage()).toContain('ORD-400');
    expect(component.successMessage()).toBeNull();
  }));

  it('maps immediate API validation error codes', () => {
    tradingServiceSpy.placeOrder.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: {
              errorCode: 'VAL-422',
              message: 'Validation failed',
              timestamp: new Date().toISOString(),
            },
          })
      )
    );

    component.orderForm.setValue({ symbol: 'AAPL', side: 'BUY', quantity: 10, price: 150 });
    component.onSubmit();

    expect(component.errorMessage()).toContain('VAL-422');
  });

  it('loads order history table data', () => {
    expect(tradingServiceSpy.getAccountOrders).toHaveBeenCalledWith(account.id);
    expect(component.orderHistory().length).toBe(1);
    expect(component.orderHistory()[0].symbol).toBe('AAPL');
  });

  it('shows status badges for FILLED, REJECTED, and CANCELLED', () => {
    component.orderHistory.set([
      {
        id: 'filled-1',
        dateTime: new Date('2026-01-01T10:00:00Z').toISOString(),
        symbol: 'AAPL',
        side: 'BUY',
        quantity: 10,
        price: 100,
        status: 'FILLED',
      },
      {
        id: 'rejected-1',
        dateTime: new Date('2026-01-01T11:00:00Z').toISOString(),
        symbol: 'MSFT',
        side: 'SELL',
        quantity: 5,
        price: 120,
        status: 'REJECTED',
      },
      {
        id: 'cancelled-1',
        dateTime: new Date('2026-01-01T12:00:00Z').toISOString(),
        symbol: 'GOOGL',
        side: 'SELL',
        quantity: 2,
        price: 130,
        status: 'CANCELLED',
      },
    ]);
    component.orderHistoryLoading.set(false);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('.status-filled')?.textContent).toContain('FILLED');
    expect(element.querySelector('.status-rejected')?.textContent).toContain('REJECTED');
    expect(element.querySelector('.status-cancelled')?.textContent).toContain('CANCELLED');
  });

  it('shows a friendly empty message when history is empty', () => {
    component.orderHistory.set([]);
    component.orderHistoryLoading.set(false);
    component.orderHistoryError.set(null);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('No previous orders yet. Your completed and cancelled orders will appear here.');
  });

  it('fills Price with the live quote when a symbol is picked', () => {
    const marketData = TestBed.inject(MarketDataService);
    spyOn(marketData, 'getQuotes').and.returnValue(
      of([
        {
          symbol: 'AAPL',
          price: 187.42,
          previousClose: 185,
          change: 2.42,
          changePercent: 1.31,
          tradedAt: null,
        },
      ])
    );

    component.orderForm.controls.symbol.setValue('AAPL');

    expect(marketData.getQuotes).toHaveBeenCalledWith(['AAPL']);
    expect(component.orderForm.controls.price.value).toBe(187.42);
    expect(component.priceLoading()).toBeFalse();
  });

  it('keeps the entered price when the live quote cannot be loaded', () => {
    const marketData = TestBed.inject(MarketDataService);
    spyOn(marketData, 'getQuotes').and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    component.orderForm.controls.price.setValue(150);

    component.orderForm.controls.symbol.setValue('AAPL');

    expect(component.orderForm.controls.price.value).toBe(150);
    expect(component.priceLoading()).toBeFalse();
  });

  it('does not show admin controls to a trader', () => {
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('#admin-account')).toBeNull();
    expect(element.querySelector('.order-panel')).not.toBeNull();
    expect(tradingServiceSpy.getAccounts).not.toHaveBeenCalled();
  });

  describe('for an admin', () => {
    const otherAccount = { ...account, id: 12, accountNumber: 'ACC-U000012', holderName: 'Trader Two' };

    function createAdminDashboard(): HTMLElement {
      authServiceSpy.isAdmin.and.returnValue(true);
      tradingServiceSpy.getAccounts.and.returnValue(of([otherAccount, account]));
      tradingServiceSpy.getAccountOrders.calls.reset();
      fixture = TestBed.createComponent(DashboardComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      return fixture.nativeElement;
    }

    it('lists every account and shows the first one, read-only', () => {
      const element = createAdminDashboard();

      expect(tradingServiceSpy.getAccounts).toHaveBeenCalled();
      const options = Array.from(element.querySelectorAll('#admin-account option')).map((option) =>
        option.textContent?.trim()
      );
      expect(options).toEqual(['ACC-U000011 - Trader One', 'ACC-U000012 - Trader Two']);
      expect(component.account()?.id).toBe(account.id);
      expect(tradingServiceSpy.getAccountOrders).toHaveBeenCalledWith(account.id);
      expect(element.querySelector('.order-panel')).toBeNull();
      expect(element.textContent).toContain('Admin view: read-only');
    });

    it('switches to the chosen account', () => {
      tradingServiceSpy.getAccount.and.returnValue(of(otherAccount));
      const element = createAdminDashboard();

      const select = element.querySelector<HTMLSelectElement>('#admin-account')!;
      select.value = String(otherAccount.id);
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(tradingServiceSpy.getAccount).toHaveBeenCalledWith(otherAccount.id);
      expect(tradingServiceSpy.getAccountOrders).toHaveBeenCalledWith(otherAccount.id);
      expect(component.account()?.holderName).toBe('Trader Two');
    });

    it('opens the analytics dashboard', () => {
      const element = createAdminDashboard();

      Array.from(element.querySelectorAll('button'))
        .find((button) => button.textContent?.trim() === 'Analytics')!
        .click();

      expect(analyticsSpy.open).toHaveBeenCalled();
    });

    it('does not place orders', () => {
      createAdminDashboard();

      component.onSubmit();

      expect(tradingServiceSpy.placeOrder).not.toHaveBeenCalled();
    });

    it('says so when there are no accounts', () => {
      authServiceSpy.isAdmin.and.returnValue(true);
      tradingServiceSpy.getAccounts.and.returnValue(of([]));
      fixture = TestBed.createComponent(DashboardComponent);
      fixture.detectChanges();

      expect(fixture.componentInstance.errorMessage()).toBe('There are no trading accounts yet.');
    });
  });
});
