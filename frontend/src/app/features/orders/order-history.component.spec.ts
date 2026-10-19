/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { AccountStore } from '../../core/trading/account.store';
import { ACCOUNT, tradingServiceStub } from '../../core/trading/testing/trading-fixtures';
import { TradingService } from '../../core/trading/trading.service';
import { OrderHistoryComponent } from './order-history.component';

describe('OrderHistoryComponent', () => {
  let fixture: ComponentFixture<OrderHistoryComponent>;
  let tradingService: jasmine.SpyObj<TradingService>;

  const create = (isSupport = false): HTMLElement => {
    TestBed.configureTestingModule({
      imports: [OrderHistoryComponent],
      providers: [
        provideRouter([]),
        AccountStore,
        { provide: TradingService, useValue: tradingService },
        { provide: AuthService, useValue: { isSupport: () => isSupport } },
      ],
    });
    fixture = TestBed.createComponent(OrderHistoryComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  const symbolsShown = (): string[] =>
    Array.from(fixture.nativeElement.querySelectorAll('tbody .symbol') as NodeListOf<HTMLElement>).map((cell) =>
      cell.textContent!.trim()
    );

  beforeEach(() => {
    tradingService = tradingServiceStub();
    tradingService.getAccountOrders.and.returnValue(
      of([
        {
          id: 'open-1',
          accountId: ACCOUNT.id,
          symbol: 'AAPL',
          side: 'BUY',
          quantity: 10,
          price: 150,
          status: 'NEW',
          createdOn: '2026-01-01T12:00:00Z',
        },
      ])
    );
    tradingService.getAccountOrderHistory.and.returnValue(
      of([
        {
          orderId: 'filled-1',
          accountId: ACCOUNT.id,
          symbol: 'MSFT',
          side: 'SELL',
          quantity: 5,
          price: 120,
          status: 'FILLED',
          orderCreatedOn: '2026-01-01T10:00:00Z',
          deletedOn: '2026-01-01T10:00:05Z',
        },
        {
          orderId: 'rejected-1',
          accountId: ACCOUNT.id,
          symbol: 'TSLA',
          side: 'BUY',
          quantity: 2,
          price: 130,
          status: 'REJECTED',
          orderCreatedOn: '2026-01-01T11:00:00Z',
          deletedOn: '2026-01-01T11:00:05Z',
        },
        {
          orderId: 'cancelled-1',
          accountId: ACCOUNT.id,
          symbol: 'GOOGL',
          side: 'SELL',
          quantity: 1,
          price: 140,
          status: 'CANCELLED',
          orderCreatedOn: '2026-01-01T09:00:00Z',
          deletedOn: '2026-01-01T09:30:00Z',
        },
      ])
    );
  });

  it('lists open and archived orders of the account, newest first', () => {
    create();

    expect(tradingService.getAccountOrders).toHaveBeenCalledWith(ACCOUNT.id);
    expect(tradingService.getAccountOrderHistory).toHaveBeenCalledWith(ACCOUNT.id);
    expect(symbolsShown()).toEqual(['AAPL', 'TSLA', 'MSFT', 'GOOGL']);
  });

  it('shows status badges for FILLED, REJECTED, and CANCELLED', () => {
    const element = create();

    expect(element.querySelector('.status-filled')?.textContent).toContain('FILLED');
    expect(element.querySelector('.status-rejected')?.textContent).toContain('REJECTED');
    expect(element.querySelector('.status-cancelled')?.textContent).toContain('CANCELLED');
  });

  it('filters by status', () => {
    const element = create();

    Array.from(element.querySelectorAll('.segmented button') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.textContent?.trim() === 'Filled')!
      .click();
    fixture.detectChanges();

    expect(symbolsShown()).toEqual(['MSFT']);
    expect(element.querySelector('.count')?.textContent).toContain('1 of 4 orders');
  });

  it('links each symbol to its trade page', () => {
    const element = create();

    expect(element.querySelector('tbody .symbol')?.getAttribute('href')).toBe('/trade/AAPL');
  });

  it('shows a friendly empty message when there are no orders', () => {
    tradingService.getAccountOrders.and.returnValue(of([]));
    tradingService.getAccountOrderHistory.and.returnValue(of([]));

    const element = create();

    expect(element.textContent).toContain('No previous orders yet. Your completed and cancelled orders will appear here.');
  });

  it('shows why the orders could not be loaded', () => {
    tradingService.getAccountOrderHistory.and.returnValue(
      throwError(() => ({ error: { errorCode: 'SYS-500', message: 'History is down' } }))
    );

    const element = create();

    expect(element.querySelector('.note.error')?.textContent).toContain('History is down');
  });

  describe('cancelling', () => {
    const cancelButtons = (): HTMLButtonElement[] =>
      Array.from(fixture.nativeElement.querySelectorAll('tbody button') as NodeListOf<HTMLButtonElement>);
    const statusOf = (symbol: string): string =>
      (Array.from(fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLElement>)
        .find((row) => row.textContent!.includes(symbol))!
        .querySelector('.status-badge') as HTMLElement).textContent!.trim();

    it('offers Cancel only on open orders', () => {
      create();

      expect(cancelButtons().map((button) => button.getAttribute('aria-label'))).toEqual(['Cancel BUY 10 AAPL']);
    });

    it('marks the order cancelled when the API cancels it', () => {
      tradingService.cancelOrder.and.returnValue(
        of({ id: 'open-1', accountId: ACCOUNT.id, symbol: 'AAPL', side: 'BUY', quantity: 10, price: 150,
             status: 'CANCELLED', createdOn: '2026-01-01T12:00:00Z' })
      );
      create();
      const store = TestBed.inject(AccountStore);
      spyOn(store, 'refresh');

      cancelButtons()[0].click();
      fixture.detectChanges();

      expect(tradingService.cancelOrder).toHaveBeenCalledWith('open-1');
      // The cash or shares the order reserved are released
      expect(store.refresh).toHaveBeenCalled();
      expect(statusOf('AAPL')).toBe('CANCELLED');
      expect(cancelButtons()).toHaveSize(0);
    });

    it('explains when the order filled before it could be cancelled, and shows its status', () => {
      tradingService.cancelOrder.and.returnValue(
        throwError(() => ({ status: 409, error: { errorCode: 'ORD-409', message: 'Only NEW orders can be cancelled' } }))
      );
      tradingService.getOrder.and.returnValue(
        of({ id: 'open-1', accountId: ACCOUNT.id, symbol: 'AAPL', side: 'BUY', quantity: 10, price: 150,
             status: 'FILLED', createdOn: '2026-01-01T12:00:00Z' })
      );
      const element = create();

      cancelButtons()[0].click();
      fixture.detectChanges();

      expect(element.querySelector('[role="alert"]')!.textContent).toContain(
        'could not be cancelled: it has already been filled or cancelled'
      );
      expect(statusOf('AAPL')).toBe('FILLED');
    });

    it('gives support, who view accounts read-only, no Cancel button', () => {
      tradingService.getAccounts.and.returnValue(of([ACCOUNT]));
      create(true);

      expect(cancelButtons()).toHaveSize(0);
    });
  });
});
