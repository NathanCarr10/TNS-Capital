import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { MarketQuote } from './trading.models';

@Injectable({ providedIn: 'root' })
export class MarketDataService {
  private readonly http = inject(HttpClient);

  getQuotes(symbols: string[]): Observable<MarketQuote[]> {
    const params = new HttpParams().set('symbols', symbols.join(','));
    return this.http.get<MarketQuote[]>('/api/v1/prices', { params });
  }
}
