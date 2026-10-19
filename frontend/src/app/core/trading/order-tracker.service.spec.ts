/// <reference types="jasmine" />

import { HttpErrorResponse } from '@angular/common/http';
import { EnvironmentInjector, createEnvironmentInjector } from '@angular/core';
import { TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { OrderResponse } from '../../api/trading';
import { AuthService } from '../auth/auth.service';
import { ToastService } from '../notifications/toast.service';
import { AccountStore } from './account.store';
import { OrderOutcome, OrderTrackerService } from './order-tracker.service';
import { ACCOUNT, tradingServiceStub } from './testing/trading-fixtures';
import { TradingService } from './trading.service';

describe('OrderTrackerService', () => {
  let tradingService: jasmine.SpyObj<TradingService>;
  let tracker: OrderTrackerService;
  let toast: ToastService;
  let store: AccountStore;

  const order = (overrides: Partial<OrderResponse>): OrderResponse => ({
    id: 'ord-1',
    accountId: ACCOUNT.id,
    symbol: 'AAPL',
    side: 'BUY',
    quantity: 10,
    price: 150,
    status: 'FILLED',
    createdOn: new Date().toISOString(),
    ...overrides,
  });

  const toasts = () => toast.toasts().map(({ kind, message }) => ({ kind, message }));

  beforeEach(() => {
    tradingService = tradingServiceStub();
    TestBed.configureTestingModule({
      providers: [
        AccountStore,
        OrderTrackerService,
        { provide: TradingService, useValue: tradingService },
        { provide: AuthService, useValue: { isSupport: () => false } },
      ],
    });
    tracker = TestBed.inject(OrderTrackerService);
    toast = TestBed.inject(ToastService);
    store = TestBed.inject(AccountStore);
  });

  it('toasts a fill and reloads the account', fakeAsync(() => {
    spyOn(store, 'refresh');
    tradingService.getOrder.and.returnValue(of(order({})));
    let outcome: OrderOutcome | undefined;

    tracker.track('ord-1').subscribe((result) => (outcome = result));
    tick();

    expect(outcome?.status).toBe('FILLED');
    expect(toasts()).toEqual([{ kind: 'success', message: 'Order filled: BUY 10 AAPL at 150.00.' }]);
    expect(store.refresh).toHaveBeenCalledTimes(1);
    flush();
  }));

  it('toasts a rejection with its mapped reason', fakeAsync(() => {
    tradingService.getOrder.and.returnValue(
      of(order({ side: 'SELL', status: 'REJECTED', statusReason: 'Insufficient holdings for AAPL' }))
    );

    tracker.track('ord-1').subscribe();
    tick();

    expect(toasts()).toEqual([{ kind: 'error', message: 'SELL AAPL rejected: Insufficient holdings (ORD-409).' }]);
    flush();
  }));

  it('keeps polling while the order is open, including before it is visible', fakeAsync(() => {
    tradingService.getOrder.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 404 })),
      of(order({ status: 'NEW' })),
      of(order({}))
    );

    tracker.track('ord-1').subscribe();
    tick(OrderTrackerService.POLL_INTERVAL_MS * 2);

    expect(tradingService.getOrder).toHaveBeenCalledTimes(3);
    expect(toasts().map((item) => item.kind)).toEqual(['success']);
    flush();
  }));

  it('stops quietly when the trader cancels the order', fakeAsync(() => {
    tradingService.getOrder.and.returnValue(of(order({ status: 'CANCELLED' })));
    let outcome: OrderOutcome | undefined;

    tracker.track('ord-1').subscribe((result) => (outcome = result));
    tick();

    expect(outcome?.status).toBe('CANCELLED');
    expect(toasts()).toEqual([]);
  }));

  it('says the order is still processing when it does not settle in time', fakeAsync(() => {
    tradingService.getOrder.and.returnValue(of(order({ status: 'NEW' })));

    tracker.track('ord-9').subscribe();
    tick(OrderTrackerService.POLL_INTERVAL_MS * OrderTrackerService.POLL_MAX_ATTEMPTS);

    expect(tradingService.getOrder).toHaveBeenCalledTimes(OrderTrackerService.POLL_MAX_ATTEMPTS);
    expect(toasts()).toEqual([
      { kind: 'info', message: 'The order is still processing. Check the order history for its status.' },
    ]);
    flush();
  }));

  it('toasts when the status cannot be read', fakeAsync(() => {
    tradingService.getOrder.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    tracker.track('ord-1').subscribe();
    tick();

    expect(toasts()).toEqual([{ kind: 'info', message: 'Failed to retrieve the final status of the order.' }]);
    flush();
  }));

  it('stops tracking without a toast when the trader signs out', fakeAsync(() => {
    // A shell of its own, which signing out destroys
    const injector = createEnvironmentInjector([OrderTrackerService], TestBed.inject(EnvironmentInjector));
    const scoped = injector.get(OrderTrackerService);
    tradingService.getOrder.and.returnValue(of(order({ status: 'NEW' })));

    scoped.track('ord-1').subscribe();
    tick();
    injector.destroy();
    tick(OrderTrackerService.POLL_INTERVAL_MS * 5);

    expect(tradingService.getOrder).toHaveBeenCalledTimes(1);
    expect(toasts()).toEqual([]);
  }));
});
