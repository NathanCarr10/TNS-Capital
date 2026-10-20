import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { MarketQuote, NewsHeadlineResponse, PriceControllerService } from '../../api/trading';
import { BarTimeframe, PriceBar } from './trading.models';

@Injectable({ providedIn: 'root' })
export class MarketDataService {
  private readonly priceApi = inject(PriceControllerService);

  getQuotes(symbols: string[]): Observable<MarketQuote[]> {
    return this.priceApi.getQuotes(symbols);
  }

  /** The most recent bars of a symbol, oldest first. */
  getBars(symbol: string, timeframe: BarTimeframe, limit: number): Observable<PriceBar[]> {
    // The spec leaves PriceBar's fields optional, but the API always sends them all
    return this.priceApi.getBars(symbol, timeframe, limit) as Observable<PriceBar[]>;
  }

  /** The latest headlines about a symbol, newest first; empty when there are none. */
  getNews(symbol: string): Observable<NewsHeadlineResponse[]> {
    return this.priceApi.getNews(symbol);
  }
}
