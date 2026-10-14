import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AuthenticationService, CurrentUser } from '../../api/auth';
import {
  AccountControllerService,
  AccountResponse,
  CashTransactionRequest,
  InstrumentControllerService,
  InstrumentResponse,
  OrderControllerService,
  OrderHistoryResponse,
  OrderResponse,
  PlaceOrderRequest,
  PlaceOrderResponse,
  PositionResponse,
} from '../../api/trading';

@Injectable({ providedIn: 'root' })
export class TradingService {
  private readonly authApi = inject(AuthenticationService);
  private readonly accountApi = inject(AccountControllerService);
  private readonly instrumentApi = inject(InstrumentControllerService);
  private readonly orderApi = inject(OrderControllerService);

  getCurrentUserProfile(): Observable<CurrentUser> {
    return this.authApi.me();
  }

  /** Every account for an admin; a trader gets only their own. */
  getAccounts(): Observable<AccountResponse[]> {
    return this.accountApi.getAllAccounts();
  }

  getAccount(accountId: number): Observable<AccountResponse> {
    return this.accountApi.getAccount(accountId);
  }

  deposit(accountId: number, request: CashTransactionRequest): Observable<AccountResponse> {
    return this.accountApi.deposit(accountId, request);
  }

  getInstruments(): Observable<InstrumentResponse[]> {
    return this.instrumentApi.getAllInstruments();
  }

  getAccountPositions(accountId: number): Observable<PositionResponse[]> {
    return this.accountApi.getAccountPositions1(accountId);
  }

  getAccountOrders(accountId: number): Observable<OrderResponse[]> {
    return this.accountApi.getAccountOrders(accountId);
  }

  getAccountOrderHistory(accountId: number): Observable<OrderHistoryResponse[]> {
    return this.orderApi.getAccountOrderHistory(accountId);
  }

  placeOrder(request: PlaceOrderRequest): Observable<PlaceOrderResponse> {
    return this.orderApi.placeOrder(request);
  }

  getOrder(orderId: string): Observable<OrderResponse> {
    return this.orderApi.getOrder(orderId);
  }
}
