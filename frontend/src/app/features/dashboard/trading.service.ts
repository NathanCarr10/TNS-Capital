import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AccountResponse,
  InstrumentResponse,
  OrderAcceptedResponse,
  OrderResponse,
  PlaceOrderRequest,
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

  placeOrder(request: PlaceOrderRequest): Observable<OrderAcceptedResponse> {
    return this.http.post<OrderAcceptedResponse>('/api/v1/orders', request);
  }

  getOrder(orderId: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`/api/v1/orders/${orderId}`);
  }
}
