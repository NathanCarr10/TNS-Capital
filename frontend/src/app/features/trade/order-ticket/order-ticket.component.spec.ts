/// <reference types="jasmine" />

import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { OrderResponse, PlaceOrderResponse } from '../../../api/trading';
import { AuthService } from '../../../core/auth/auth.service';
import { ToastService } from '../../../core/notifications/toast.service';
import { AccountStore } from '../../../core/trading/account.store';
import { OrderTrackerService } from '../../../core/trading/order-tracker.service';
import { ACCOUNT, INSTRUMENTS, tradingServiceStub } from '../../../core/trading/testing/trading-fixtures';
import { TradingService } from '../../../core/trading/trading.service';
import { OrderTicketComponent } from './order-ticket.component';

describe('OrderTicketComponent', () => {
  let fixture: ComponentFixture<OrderTicketComponent>;
  let component: OrderTicketComponent;
  let tradingService: jasmine.SpyObj<TradingService>;
  let isAdmin: boolean;

  const create = (livePrice: number | null = 150, instrument = INSTRUMENTS[0]): void => {
    TestBed.configureTestingModule({
      imports: [OrderTicketComponent],
      providers: [
        AccountStore,
        OrderTrackerService,
        { provide: TradingService, useValue: tradingService },
        { provide: AuthService, useValue: { isAdmin: () => isAdmin } },
      ],
    });
    fixture = TestBed.createComponent(OrderTicketComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('symbol', instrument.symbol);
    fixture.componentRef.setInput('instrument', instrument);
    fixture.componentRef.setInput('livePrice', livePrice);
    fixture.detectChanges();
  };

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

  const toastMessages = (): string[] => TestBed.inject(ToastService).toasts().map((toast) => toast.message);

  const submitButton = (): HTMLButtonElement => fixture.nativeElement.querySelector('button[type="submit"]');

  beforeEach(() => {
    tradingService = tradingServiceStub();
    isAdmin = false;
  });

  it('fills the limit price with the live quote', () => {
    create(187.42);

    expect(component.orderForm.controls.price.value).toBe(187.42);
    expect(submitButton().textContent).toContain('Buy 1 AAPL');
  });

  it('keeps a price the trader entered when a new quote arrives', () => {
    create(150);
    component.orderForm.controls.price.setValue(140);

    fixture.componentRef.setInput('livePrice', 155);
    fixture.detectChanges();

    expect(component.orderForm.controls.price.value).toBe(140);
  });

  it('submits a BUY limit order and toasts the fill', fakeAsync(() => {
    create();
    tradingService.placeOrder.and.returnValue(of(accepted('ord-buy')));
    tradingService.getOrder.and.returnValue(of(order({ id: 'ord-buy' })));

    component.orderForm.patchValue({ side: 'BUY', quantity: 10, price: 150 });
    component.onSubmit();
    tick();

    expect(tradingService.placeOrder).toHaveBeenCalledWith(
      jasmine.objectContaining({ accountId: ACCOUNT.id, symbol: 'AAPL', side: 'BUY', quantity: 10, price: 150 })
    );
    expect(toastMessages()).toEqual(['Order filled: BUY 10 AAPL at 150.00.']);
    expect(component.successMessage()).toBeNull();
    expect(component.errorMessage()).toBeNull();
    expect(component.isTracking()).toBeFalse();
    // The fill moves cash and holdings, so both are reloaded for every page
    expect(tradingService.getAccount).toHaveBeenCalledTimes(2);
    expect(tradingService.getAccountPositions).toHaveBeenCalledTimes(2);
    flush();
  }));

  it('submits a SELL order', fakeAsync(() => {
    create();
    tradingService.placeOrder.and.returnValue(of(accepted('ord-sell')));
    tradingService.getOrder.and.returnValue(of(order({ id: 'ord-sell', side: 'SELL', quantity: 5, price: 155 })));

    component.orderForm.patchValue({ side: 'SELL', quantity: 5, price: 155 });
    component.onSubmit();
    tick();

    expect(tradingService.placeOrder).toHaveBeenCalledWith(jasmine.objectContaining({ side: 'SELL', quantity: 5 }));
    expect(toastMessages()).toEqual(['Order filled: SELL 5 AAPL at 155.00.']);
    flush();
  }));

  it('sends no price for a market order, so it fills at the latest price', fakeAsync(() => {
    create();
    tradingService.placeOrder.and.returnValue(of(accepted('ord-mkt')));
    tradingService.getOrder.and.returnValue(of(order({ id: 'ord-mkt' })));

    component.orderForm.patchValue({ orderType: 'MARKET', quantity: 2 });
    component.onSubmit();
    tick();

    const request = tradingService.placeOrder.calls.mostRecent().args[0];
    expect(request.price).toBeUndefined();
    expect(request.quantity).toBe(2);
    flush();
  }));

  it('needs a price for a limit order but not for a market order', () => {
    create(null);

    expect(component.orderForm.controls.price.value).toBeNull();
    expect(component.orderForm.invalid).toBeTrue();

    component.orderForm.controls.orderType.setValue('MARKET');

    expect(component.orderForm.valid).toBeTrue();
  });

  it('enforces quantity validation', () => {
    create();
    component.orderForm.patchValue({ quantity: 0 });

    component.onSubmit();

    expect(component.orderForm.controls.quantity.invalid).toBeTrue();
    expect(tradingService.placeOrder).not.toHaveBeenCalled();
  });

  it('enforces price validation', () => {
    create();
    component.orderForm.patchValue({ price: 0 });

    component.onSubmit();

    expect(component.orderForm.controls.price.invalid).toBeTrue();
    expect(tradingService.placeOrder).not.toHaveBeenCalled();
  });

  it('toasts the rejection reason for insufficient funds', fakeAsync(() => {
    create();
    tradingService.placeOrder.and.returnValue(of(accepted('ord-rej')));
    tradingService.getOrder.and.returnValue(
      of(order({ id: 'ord-rej', status: 'REJECTED', statusReason: 'Insufficient funds for account 11' }))
    );

    component.onSubmit();
    tick();

    expect(toastMessages()).toEqual(['BUY AAPL rejected: Insufficient funds (ORD-400).']);
    expect(component.successMessage()).toBeNull();
    expect(component.isTracking()).toBeFalse();
    flush();
  }));

  it('shows the order as tracking until it settles', fakeAsync(() => {
    create();
    tradingService.placeOrder.and.returnValue(of(accepted('ord-open')));
    tradingService.getOrder.and.returnValue(of(order({ id: 'ord-open', status: 'NEW' })));

    component.onSubmit();
    tick();
    fixture.detectChanges();

    expect(component.isTracking()).toBeTrue();
    expect(submitButton().textContent).toContain('Tracking order...');
    expect(fixture.nativeElement.querySelector('.note.success')?.textContent).toContain('Tracking order ord-open');

    tradingService.getOrder.and.returnValue(of(order({ id: 'ord-open' })));
    tick(OrderTrackerService.POLL_INTERVAL_MS);

    expect(component.isTracking()).toBeFalse();
    expect(toastMessages()).toHaveSize(1);
    flush();
  }));

  it('maps immediate API validation error codes', () => {
    create();
    tradingService.placeOrder.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { errorCode: 'VAL-422', message: 'Validation failed', timestamp: new Date().toISOString() },
          })
      )
    );

    component.onSubmit();

    expect(component.errorMessage()).toContain('VAL-422');
  });

  it('explains an order refused because the account is not active', () => {
    create();
    tradingService.placeOrder.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 403,
            error: { errorCode: 'ACC-403', message: 'Account not active', timestamp: new Date().toISOString() },
          })
      )
    );

    component.onSubmit();

    expect(component.errorMessage()).toBe('Account inactive (ACC-403).');
  });

  it('estimates the cost and warns when it is more than the cash', () => {
    create(100);
    component.orderForm.patchValue({ quantity: 60 });
    fixture.detectChanges();

    expect(component.estimatedTotal()).toBe(6000);
    expect(fixture.nativeElement.querySelector('.hint.warning')?.textContent).toContain('more than your available cash');
  });

  it('warns when selling more than is held', () => {
    tradingService.getAccountPositions.and.returnValue(
      of([{ accountId: ACCOUNT.id, symbol: 'AAPL', quantity: 3, averageCost: 100, marketValue: null }])
    );
    create();
    component.orderForm.patchValue({ side: 'SELL', quantity: 5 });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.hint.warning')?.textContent).toContain('You hold 3 AAPL');
  });

  it('pre-fills the limit with the live price rounded to the cent', () => {
    create(227.4651);

    expect(component.orderForm.controls.price.value).toBe(227.47);
    expect(fixture.nativeElement.querySelector('.hint.warning')).toBeNull();
  });

  it('warns that a buy limit below the market price will not fill', () => {
    create(150.004);
    component.orderForm.patchValue({ side: 'BUY', price: 149.98 });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.hint.warning')?.textContent).toContain(
      'buy limit below the market price (150.00) will not fill'
    );
  });

  it('warns that a sell limit above the market price will not fill', () => {
    tradingService.getAccountPositions.and.returnValue(
      of([{ accountId: ACCOUNT.id, symbol: 'AAPL', quantity: 10, averageCost: 100, marketValue: null }])
    );
    create(150);
    component.orderForm.patchValue({ side: 'SELL', quantity: 1, price: 10000 });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.hint.warning')?.textContent).toContain(
      'sell limit above the market price (150.00) will not fill'
    );
  });

  it('does not warn about a limit that crosses the market', () => {
    create(150);
    component.orderForm.patchValue({ side: 'BUY', quantity: 1, price: 151 });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.hint.warning')).toBeNull();
  });

  it('shows the server explanation when a limit is refused as not marketable', () => {
    create();
    const message = 'A BUY limit must be at or above the market price (227.47) to fill; this order would not fill at 1.00';
    tradingService.placeOrder.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { errorCode: 'ORD-422', message, timestamp: new Date().toISOString() },
          })
      )
    );

    component.onSubmit();

    expect(component.errorMessage()).toBe(message);
  });

  it('cannot trade an instrument that is not tradable', () => {
    create(10, INSTRUMENTS[2]);

    expect(submitButton().disabled).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('OLD is not tradable');
  });

  it('is read-only for an admin', () => {
    isAdmin = true;
    tradingService.getCurrentUserProfile.and.returnValue(of({ username: 'admin', roles: ['ADMIN'], accountId: null }));
    tradingService.getAccounts.and.returnValue(of([ACCOUNT]));
    create();

    component.onSubmit();

    expect(tradingService.placeOrder).not.toHaveBeenCalled();
    expect(submitButton().disabled).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Admin view: read-only');
  });
});

function accepted(orderId: string): PlaceOrderResponse {
  return { orderId, status: 'ACCEPTED', price: null, message: 'Order accepted for processing.' };
}
