import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { MarketQuote, PriceControllerService } from '../../api/trading';

@Injectable({ providedIn: 'root' })
export class MarketDataService {
  private readonly priceApi = inject(PriceControllerService);

  getQuotes(symbols: string[]): Observable<MarketQuote[]> {
    return this.priceApi.getQuotes(symbols);
  }
}
