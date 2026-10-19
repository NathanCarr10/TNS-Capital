/// <reference types="jasmine" />

import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { MarketQuote, PositionResponse } from '../../api/trading';
import { AuthService } from '../../core/auth/auth.service';
import { AccountStore } from '../../core/trading/account.store';
import { OrderTrackerService } from '../../core/trading/order-tracker.service';
import { MarketDataService } from '../../core/trading/market-data.service';
import { tradingServiceStub } from '../../core/trading/testing/trading-fixtures';
import { PriceBar } from '../../core/trading/trading.models';
import { TradingService } from '../../core/trading/trading.service';
import { TradeComponent } from './trade.component';

describe('TradeComponent', () => {
  let fixture: ComponentFixture<TradeComponent>;
  let marketDataServiceSpy: jasmine.SpyObj<MarketDataService>;
  let tradingService: jasmine.SpyObj<TradingService>;
  let router: Router;

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

  /** The value under a label such as Quantity, within the given section. */
  const stat = (section: string, label: string): string => {
    const terms = Array.from(fixture.nativeElement.querySelectorAll(`${section} dt`)) as HTMLElement[];
    const term = terms.find((dt) => dt.textContent?.trim() === label);
    return term?.nextElementSibling?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  };

  const givenPositions = (positions: PositionResponse[]) =>
    tradingService.getAccountPositions.and.returnValue(of(positions));

  beforeEach(async () => {
    marketDataServiceSpy = jasmine.createSpyObj<MarketDataService>('MarketDataService', ['getQuotes', 'getBars']);
    marketDataServiceSpy.getQuotes.and.returnValue(of([aaplQuote]));
    marketDataServiceSpy.getBars.and.returnValue(of(bars));
    tradingService = tradingServiceStub();

    await TestBed.configureTestingModule({
      imports: [TradeComponent],
      providers: [
        provideRouter([]),
        AccountStore,
        OrderTrackerService,
        { provide: MarketDataService, useValue: marketDataServiceSpy },
        { provide: TradingService, useValue: tradingService },
        { provide: AuthService, useValue: { isSupport: () => false } },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  // Created per test, as the component loads its data on creation
  const open = (symbol: string): void => {
    fixture = TestBed.createComponent(TradeComponent);
    fixture.componentRef.setInput('symbol', symbol);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
  };

  it('shows the name, symbol, price and daily change', fakeAsync(() => {
    open('aapl');

    expect(text('h1')).toBe('Apple Inc.');
    expect(text('.meta')).toContain('AAPL');
    expect(text('.quote .price')).toBe('$190.00');
    expect(text('.quote .change')).toContain('+10.00 (+5.56%)');
    expect(fixture.nativeElement.querySelector('.quote .change-up')).not.toBeNull();
    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledWith(['AAPL']);
    discardPeriodicTasks();
  }));

  it("shows the day's open, range and volume from the latest daily bar", fakeAsync(() => {
    open('AAPL');

    expect(marketDataServiceSpy.getBars).toHaveBeenCalledWith('AAPL', '1Day', 1);
    expect(stat('.stats', 'Open')).toBe('$180.00');
    expect(stat('.stats', 'Day high')).toBe('$191.00');
    expect(stat('.stats', 'Day low')).toBe('$179.00');
    expect(stat('.stats', 'Prev. close')).toBe('$180.00');
    expect(stat('.stats', 'Volume')).toBe('1,200');
    discardPeriodicTasks();
  }));

  it('leaves out the position section when the user holds none of the symbol', fakeAsync(() => {
    givenPositions([{ accountId: 11, symbol: 'MSFT', quantity: 5, averageCost: 300, marketValue: null }]);

    open('AAPL');

    expect(fixture.nativeElement.querySelector('.holding-panel')).toBeNull();
    expect(text('.meta')).not.toContain('Held');
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

    expect(stat('.holding-panel', 'Quantity')).toBe('10');
    expect(stat('.holding-panel', 'Avg price')).toBe('$150.00');
    expect(stat('.holding-panel', 'Cost basis')).toBe('$1,500.00');
    expect(stat('.holding-panel', 'Market value')).toBe('$1,900.00');
    expect(stat('.holding-panel', 'Unrealised return')).toBe('+$400.00 +26.67%');
    expect(text('.meta')).toContain('Held');
    discardPeriodicTasks();
  }));

  it('loads daily bars by default and refetches when the bar size changes', fakeAsync(() => {
    open('AAPL');

    expect(marketDataServiceSpy.getBars).toHaveBeenCalledWith('AAPL', '1Day', TradeComponent.BAR_LIMIT);
    expect(fixture.nativeElement.querySelector('app-candlestick-chart')).not.toBeNull();

    const hourly = Array.from(
      fixture.nativeElement.querySelectorAll('.chart-panel .segmented button') as NodeListOf<HTMLButtonElement>
    ).find((button) => button.textContent?.trim() === '1H')!;
    hourly.click();
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(marketDataServiceSpy.getBars).toHaveBeenCalledWith('AAPL', '1Hour', TradeComponent.BAR_LIMIT);
    expect(hourly.getAttribute('aria-pressed')).toBe('true');
    discardPeriodicTasks();
  }));

  it('turns each moving average on and off', fakeAsync(() => {
    open('AAPL');
    const toggle = fixture.nativeElement.querySelector('.indicator-0') as HTMLButtonElement;

    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    toggle.click();
    fixture.detectChanges();

    expect(toggle.getAttribute('aria-pressed')).toBe('false');
    expect(fixture.componentInstance.shownAverages()).toEqual([50]);
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

    expect(fixture.nativeElement.querySelector('app-candlestick-chart')).not.toBeNull();
    discardPeriodicTasks();
  }));

  it('says so when no instrument has the symbol', fakeAsync(() => {
    open('NOPE');

    expect(text('.note.error')).toContain('No instrument with the symbol NOPE exists.');
    expect(fixture.nativeElement.querySelector('.chart-panel')).toBeNull();
    discardPeriodicTasks();
  }));

  it('shows a script payload from the URL as text instead of running it', fakeAsync(() => {
    open('<img src=x onerror=alert(1)>');

    expect(text('.note.error')).toContain('No instrument with the symbol <IMG SRC=X ONERROR=ALERT(1)> exists.');
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    discardPeriodicTasks();
  }));

  it('opens the first holding when no symbol is given', fakeAsync(() => {
    givenPositions([{ accountId: 11, symbol: 'MSFT', quantity: 5, averageCost: 300, marketValue: null }]);

    open('');

    expect(router.navigate).toHaveBeenCalledWith(['/trade', 'MSFT'], { replaceUrl: true });
    discardPeriodicTasks();
  }));

  it('opens the first tradable instrument when nothing is held', fakeAsync(() => {
    open('');

    expect(router.navigate).toHaveBeenCalledWith(['/trade', 'AAPL'], { replaceUrl: true });
    discardPeriodicTasks();
  }));

  it('returns to the last symbol viewed', fakeAsync(() => {
    TestBed.inject(AccountStore).recentSymbol.set('MSFT');

    open('');

    expect(router.navigate).toHaveBeenCalledWith(['/trade', 'MSFT'], { replaceUrl: true });
    discardPeriodicTasks();
  }));
});
