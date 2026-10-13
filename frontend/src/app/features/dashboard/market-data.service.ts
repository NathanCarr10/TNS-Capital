import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { BarTimeframe, MarketQuote, PriceBar } from './trading.models';

@Injectable({ providedIn: 'root' })
export class MarketDataService {
  private readonly http = inject(HttpClient);

  getQuotes(symbols: string[]): Observable<MarketQuote[]> {
    const params = new HttpParams().set('symbols', symbols.join(','));
    return this.http.get<MarketQuote[]>('/api/v1/prices', { params });
  }

  /** The most recent bars of a symbol, oldest first. */
  getBars(symbol: string, timeframe: BarTimeframe, limit: number): Observable<PriceBar[]> {
    const params = new HttpParams().set('timeframe', timeframe).set('limit', limit);
    return this.http.get<PriceBar[]>(`/api/v1/prices/${encodeURIComponent(symbol)}/bars`, { params });
  }
}
