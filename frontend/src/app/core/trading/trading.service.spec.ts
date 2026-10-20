/// <reference types="jasmine" />

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CurrentUser } from '../../api/auth';
import {
  AccountResponse,
  OrderHistoryResponse,
  OrderResponse,
  PlaceOrderRequest,
  PlaceOrderResponse,
  PositionResponse,
} from '../../api/trading';
import { provideApiClients } from '../api/api.providers';
import { TradingService } from './trading.service';

describe('TradingService', () => {
  let service: TradingService;
  let httpMock: HttpTestingController;

  const orderId = '3f2b8c1e-0000-4000-8000-000000000001';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiClients()],
    });

    service = TestBed.inject(TradingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('gets the current user profile from the auth service', () => {
    const profile: CurrentUser = { email: 'trader1@example.com', username: 'trader1', roles: ['USER'], accountId: 11 };
    let result: CurrentUser | undefined;
    service.getCurrentUserProfile().subscribe((response) => (result = response));

    const request = httpMock.expectOne('/auth/me');
    expect(request.request.method).toBe('GET');
    request.flush(profile);

    expect(result).toEqual(profile);
  });

  it('gets an account and maps it to AccountResponse', () => {
    const account: AccountResponse = {
      id: 11,
      accountNumber: 'ACC-U000011',
      holderName: 'Trader One',
      email: 'ada@example.com',
      cashBalance: 5000,
      reservedCash: 0,
      availableCash: 5000,
      status: 'ACTIVE',
      openedOn: '2026-09-01T09:30:00Z',
      lastUpdated: '2026-10-13T09:00:00Z',
    };
    let result: AccountResponse | undefined;
    service.getAccount(11).subscribe((response) => (result = response));

    const request = httpMock.expectOne('/api/v1/accounts/11');
    expect(request.request.method).toBe('GET');
    request.flush(account);

    expect(result).toEqual(account);
  });

  it('renames the account holder with a PATCH', () => {
    service.updateAccount(11, { holderName: 'Ada Lovelace' }).subscribe();

    const request = httpMock.expectOne('/api/v1/accounts/11');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ holderName: 'Ada Lovelace' });
    request.flush({});
  });

  it('gets instruments', () => {
    service.getInstruments().subscribe();

    const request = httpMock.expectOne('/api/v1/instruments');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('gets account positions, keeping a null market value', () => {
    const positions: PositionResponse[] = [
      { accountId: 11, symbol: 'AAPL', quantity: 10, reservedQuantity: 0, availableQuantity: 10, averageCost: 180.25, marketValue: null },
    ];
    let result: PositionResponse[] | undefined;
    service.getAccountPositions(11).subscribe((response) => (result = response));

    const request = httpMock.expectOne('/api/v1/accounts/11/positions');
    expect(request.request.method).toBe('GET');
    request.flush(positions);

    expect(result?.[0].marketValue).toBeNull();
  });

  it('gets active account orders', () => {
    const orders: OrderResponse[] = [
      {
        id: orderId,
        accountId: 11,
        symbol: 'AAPL',
        side: 'BUY',
        quantity: 5,
        price: 190,
        status: 'NEW',
        createdOn: '2026-10-13T09:00:00Z',
      },
    ];
    let result: OrderResponse[] | undefined;
    service.getAccountOrders(11).subscribe((response) => (result = response));

    const request = httpMock.expectOne('/api/v1/accounts/11/orders');
    expect(request.request.method).toBe('GET');
    request.flush(orders);

    expect(result).toEqual(orders);
  });

  it('gets archived account orders', () => {
    let result: OrderHistoryResponse[] | undefined;
    service.getAccountOrderHistory(11).subscribe((response) => (result = response));

    const request = httpMock.expectOne('/api/v1/orders/11/history');
    expect(request.request.method).toBe('GET');
    request.flush([]);

    expect(result).toEqual([]);
  });

  it('posts a PlaceOrderRequest and maps the PlaceOrderResponse', () => {
    const order: PlaceOrderRequest = {
      accountId: 11,
      symbol: 'AAPL',
      side: PlaceOrderRequest.SideEnum.BUY,
      quantity: 5,
      price: 190,
      idempotencyKey: 'key-1',
    };
    const accepted: PlaceOrderResponse = {
      orderId,
      status: 'ACCEPTED',
      price: 190,
      message: 'Order accepted for processing.',
    };
    let result: PlaceOrderResponse | undefined;
    service.placeOrder(order).subscribe((response) => (result = response));

    const request = httpMock.expectOne('/api/v1/orders');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(order);
    request.flush(accepted);

    expect(result).toEqual(accepted);
  });

  it('gets one order', () => {
    service.getOrder(orderId).subscribe();

    const request = httpMock.expectOne(`/api/v1/orders/${orderId}`);
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
});
