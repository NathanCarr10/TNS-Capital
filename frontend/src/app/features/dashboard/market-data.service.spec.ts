/// <reference types="jasmine" />

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { MarketQuote } from '../../api/trading';
import { provideApiClients } from '../../core/api/api.providers';
import { MarketDataService } from './market-data.service';
import { PriceBar } from './trading.models';

describe('MarketDataService', () => {
  let service: MarketDataService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiClients()],
    });
    service = TestBed.inject(MarketDataService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests quotes with one symbols parameter per symbol', () => {
    const quotes: MarketQuote[] = [
      {
        symbol: 'AAPL',
        price: 190.5,
        previousClose: 188,
        change: 2.5,
        changePercent: 1.33,
        tradedAt: '2026-10-13T14:30:00Z',
      },
    ];
    let result: MarketQuote[] | undefined;

    service.getQuotes(['AAPL', 'MSFT']).subscribe((response) => (result = response));

    const request = httpMock.expectOne((req) => req.url === '/api/v1/prices');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.getAll('symbols')).toEqual(['AAPL', 'MSFT']);
    request.flush(quotes);

    expect(result).toEqual(quotes);
  });

  it('propagates server errors', () => {
    let status: number | undefined;

    service.getQuotes(['AAPL']).subscribe({ error: (error) => (status = error.status) });

    httpMock
      .expectOne((req) => req.url === '/api/v1/prices')
      .flush(
        { errorCode: 'MKT-503', message: 'Market data is unavailable right now; try again later' },
        { status: 503, statusText: 'Service Unavailable' }
      );

    expect(status).toBe(503);
  });

  it('requests bars of one symbol with the timeframe and limit', () => {
    const bars: PriceBar[] = [
      { time: '2026-10-12T04:00:00Z', open: 188, high: 191, low: 187, close: 190.5, volume: 1200 },
    ];
    let result: PriceBar[] | undefined;

    service.getBars('AAPL', '1Hour', 120).subscribe((response) => (result = response));

    const request = httpMock.expectOne((req) => req.url === '/api/v1/prices/AAPL/bars');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('timeframe')).toBe('1Hour');
    expect(request.request.params.get('limit')).toBe('120');
    request.flush(bars);

    expect(result).toEqual(bars);
  });
});
