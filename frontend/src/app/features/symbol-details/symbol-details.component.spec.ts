/// <reference types="jasmine" />

import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { MarketDataService } from '../dashboard/market-data.service';
import { InstrumentResponse, MarketQuote, PositionResponse } from '../../api/trading';
import { PriceBar } from '../dashboard/trading.models';
import { TradingService } from '../dashboard/trading.service';
import { SymbolDetailsComponent } from './symbol-details.component';

describe('SymbolDetailsComponent', () => {
  let fixture: ComponentFixture<SymbolDetailsComponent>;
  let marketDataServiceSpy: jasmine.SpyObj<MarketDataService>;
  let tradingServiceSpy: jasmine.SpyObj<TradingService>;

  const instruments: InstrumentResponse[] = [
    { symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
    { symbol: 'MSFT', name: 'Microsoft Corp.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  ];

  const aaplQuote: MarketQuote = {
    symbol: 'AAPL',
    price: 190,
    previousClose: 180,
    change: 10,
    changePercent: 5.56,
    tradedAt: '2026-10-13T14:30:00Z',
  };

  const bars: PriceBar[] = [
    { time: '2026-10-09T04:00:00Z', open: 178, high: 182, low: 177, close: 180, volume: 1000 },
    { time: '2026-10-10T04:00:00Z', open: 180, high: 191, low: 179, close: 190, volume: 1200 },
  ];

  const text = (selector: string): string =>
    (fixture.nativeElement.querySelector(selector) as HTMLElement | null)?.textContent?.replace(/\s+/g, ' ').trim() ??
    '';

  /** The position value under a label such as Quantity. */
  const stat = (label: string): string => {
    const terms = Array.from(fixture.nativeElement.querySelectorAll('.holding-panel dt')) as HTMLElement[];
    const term = terms.find((dt) => dt.textContent?.trim() === label);
    return term?.nextElementSibling?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  };

  const givenPositions = (positions: PositionResponse[]) =>
    tradingServiceSpy.getAccountPositions.and.returnValue(of(positions));

  beforeEach(async () => {
    marketDataServiceSpy = jasmine.createSpyObj<MarketDataService>('MarketDataService', ['getQuotes', 'getBars']);
    tradingServiceSpy = jasmine.createSpyObj<TradingService>('TradingService', [
      'getInstruments',
      'getCurrentUserProfile',
      'getAccountPositions',
    ]);
    marketDataServiceSpy.getQuotes.and.returnValue(of([aaplQuote]));
    marketDataServiceSpy.getBars.and.returnValue(of(bars));
    tradingServiceSpy.getInstruments.and.returnValue(of(instruments));
    tradingServiceSpy.getCurrentUserProfile.and.returnValue(of({ username: 'trader', roles: [], accountId: 11 }));
    givenPositions([]);

    await TestBed.configureTestingModule({
      imports: [SymbolDetailsComponent],
      providers: [
        provideRouter([]),
        { provide: MarketDataService, useValue: marketDataServiceSpy },
        { provide: TradingService, useValue: tradingServiceSpy },
      ],
    }).compileComponents();
  });

  // Created per test, as the component loads its data on creation
  const open = (symbol: string): void => {
    fixture = TestBed.createComponent(SymbolDetailsComponent);
    fixture.componentRef.setInput('symbol', symbol);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
  };

  it('shows the symbol, name, price and daily change', fakeAsync(() => {
    open('aapl');

    expect(text('h1')).toBe('AAPL');
    expect(text('.instrument-name')).toBe('Apple Inc.');
    expect(text('.quote .price')).toBe('$190.00');
    expect(text('.quote .change')).toContain('+10.00 (+5.56%)');
    expect(fixture.nativeElement.querySelector('.quote .change-up')).not.toBeNull();
    expect(text('.quote-panel .stats')).toContain('$180.00');
    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledWith(['AAPL']);
    discardPeriodicTasks();
  }));

  it('leaves out the position section when the user holds none of the symbol', fakeAsync(() => {
    givenPositions([{ accountId: 11, symbol: 'MSFT', quantity: 5, averageCost: 300, marketValue: null }]);

    open('AAPL');

    expect(fixture.nativeElement.querySelector('.holding-panel')).toBeNull();
    expect(fixture.nativeElement.querySelector('.tag-held')).toBeNull();
    discardPeriodicTasks();
  }));

  it('leaves out the position section for a closed position', fakeAsync(() => {
    givenPositions([{ accountId: 11, symbol: 'AAPL', quantity: 0, averageCost: 150, marketValue: null }]);

    open('AAPL');

    expect(fixture.nativeElement.querySelector('.holding-panel')).toBeNull();
    discardPeriodicTasks();
  }));

  it('shows the position valued at the live price when the user holds the symbol', fakeAsync(() => {
    givenPositions([{ accountId: 11, symbol: 'AAPL', quantity: 10, averageCost: 150, marketValue: null }]);

    open('AAPL');

    expect(stat('Quantity')).toBe('10');
    expect(stat('Avg Price')).toBe('$150.00');
    expect(stat('Cost Basis')).toBe('$1,500.00');
    expect(stat('Market Value')).toBe('$1,900.00');
    expect(stat('Unrealised Return')).toBe('+$400.00 +26.67%');
    expect(fixture.nativeElement.querySelector('.tag-held')).not.toBeNull();
    discardPeriodicTasks();
  }));

  it('loads daily bars by default and refetches when the bar size changes', fakeAsync(() => {
    open('AAPL');

    expect(marketDataServiceSpy.getBars).toHaveBeenCalledOnceWith('AAPL', '1Day', SymbolDetailsComponent.BAR_LIMIT);
    expect(fixture.nativeElement.querySelector('app-candlestick-chart')).not.toBeNull();

    const hourly = Array.from(fixture.nativeElement.querySelectorAll('.timeframes button') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.textContent?.trim() === '1H')!;
    hourly.click();
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(marketDataServiceSpy.getBars).toHaveBeenCalledWith('AAPL', '1Hour', SymbolDetailsComponent.BAR_LIMIT);
    expect(hourly.getAttribute('aria-pressed')).toBe('true');
    discardPeriodicTasks();
  }));

  it('shows why the chart could not load and retries on request', fakeAsync(() => {
    marketDataServiceSpy.getBars.and.returnValue(
      throwError(() => ({ error: { errorCode: 'MKT-503', message: 'No 1Day bars for AAPL' } }))
    );

    open('AAPL');

    expect(text('.chart-panel .error-banner')).toContain('No 1Day bars for AAPL');
    expect(fixture.nativeElement.querySelector('app-candlestick-chart')).toBeNull();

    marketDataServiceSpy.getBars.and.returnValue(of(bars));
    (fixture.nativeElement.querySelector('.chart-panel .error-banner button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(marketDataServiceSpy.getBars).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('app-candlestick-chart')).not.toBeNull();
    discardPeriodicTasks();
  }));

  it('says so when no instrument has the symbol', fakeAsync(() => {
    open('NOPE');

    expect(text('.note.error')).toContain('No instrument with the symbol NOPE exists.');
    expect(fixture.nativeElement.querySelector('.chart-panel')).toBeNull();
    discardPeriodicTasks();
  }));
});
