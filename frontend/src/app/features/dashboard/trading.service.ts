import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AccountResponse,
  ArchivedOrderResponse,
  InstrumentResponse,
  OrderAcceptedResponse,
  OrderResponse,
  PlaceOrderRequest,
  PositionResponse,
  UserProfileResponse,
} from './trading.models';

@Injectable({ providedIn: 'root' })
export class TradingService {
  private readonly http = inject(HttpClient);

  getCurrentUserProfile(): Observable<UserProfileResponse> {
    return this.http.get<UserProfileResponse>('/auth/me');
  }

  getAccount(accountId: number): Observable<AccountResponse> {
    return this.http.get<AccountResponse>(`/api/v1/accounts/${accountId}`);
  }

  getInstruments(): Observable<InstrumentResponse[]> {
    return this.http.get<InstrumentResponse[]>('/api/v1/instruments');
  }

  getAccountPositions(accountId: number): Observable<PositionResponse[]> {
    return this.http.get<PositionResponse[]>(`/api/v1/accounts/${accountId}/positions`);
  }

  getAccountOrders(accountId: number): Observable<OrderResponse[]> {
    return this.http.get<OrderResponse[]>(`/api/v1/accounts/${accountId}/orders`);
  }

  getAccountOrderHistory(accountId: number): Observable<ArchivedOrderResponse[]> {
    return this.http.get<ArchivedOrderResponse[]>(`/api/v1/orders/${accountId}/history`);
  }

  placeOrder(request: PlaceOrderRequest): Observable<OrderAcceptedResponse> {
    return this.http.post<OrderAcceptedResponse>('/api/v1/orders', request);
  }

  getOrder(orderId: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`/api/v1/orders/${orderId}`);
  }
}
