/// <reference types="jasmine" />

import { WritableSignal, computed, signal } from '@angular/core';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { InstrumentResponse, MarketQuote } from '../../api/trading';
import { AccountStore } from '../../core/trading/account.store';
import { MarketDataService } from '../../core/trading/market-data.service';
import { INSTRUMENTS } from '../../core/trading/testing/trading-fixtures';
import { PriceBar } from '../../core/trading/trading.models';
import { TickerTapeComponent, sparklinePoints } from './ticker-tape.component';

describe('sparklinePoints', () => {
  it('needs at least two closes to draw a line', () => {
    expect(sparklinePoints([])).toBeNull();
    expect(sparklinePoints([10])).toBeNull();
  });

  it('puts the lowest close on the bottom edge and the highest on the top', () => {
    expect(sparklinePoints([10, 20, 15])).toBe('0.0,16.0 24.0,0.0 48.0,8.0');
  });

  it('draws an unchanged price through the middle', () => {
    expect(sparklinePoints([5, 5])).toBe('0.0,8.0 48.0,8.0');
  });
});

describe('TickerTapeComponent', () => {
  let fixture: ComponentFixture<TickerTapeComponent>;
  let marketData: jasmine.SpyObj<MarketDataService>;
  let instruments: WritableSignal<InstrumentResponse[] | null>;
  let heldSymbols: WritableSignal<string[]>;

  const quote = (symbol: string, price: number | null, change: number | null, changePercent: number | null): MarketQuote => ({
    symbol,
    price,
    previousClose: null,
    change,
    changePercent,
    tradedAt: null,
  });

  const bar = (close: number): PriceBar => ({ time: '2026-10-20T14:30:00Z', open: close, high: close, low: close, close, volume: 1 });

  const render = (): HTMLElement => {
    fixture = TestBed.createComponent(TickerTapeComponent);
    fixture.detectChanges();
    // The quotes store polls on a timer; let its first fetch land
    tick(0);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  // The first copy of the tape; the second is a hidden duplicate for the loop
  const items = (element: HTMLElement): HTMLAnchorElement[] =>
    Array.from(element.querySelectorAll<HTMLAnchorElement>('.group:first-child .item'));

  beforeEach(async () => {
    instruments = signal<InstrumentResponse[] | null>(INSTRUMENTS);
    heldSymbols = signal<string[]>([]);
    const accountStore = {
      instruments,
      heldSymbols,
      tradableInstruments: computed(() => (instruments() ?? []).filter((instrument) => instrument.tradable)),
    };
    marketData = jasmine.createSpyObj<MarketDataService>('MarketDataService', ['getQuotes', 'getBars']);
    marketData.getQuotes.and.returnValue(
      of([quote('AAPL', 231.4, 2.75, 1.2), quote('MSFT', 410, -1.64, -0.4)])
    );
    marketData.getBars.and.returnValue(of([bar(1), bar(2), bar(3)]));

    await TestBed.configureTestingModule({
      imports: [TickerTapeComponent],
      providers: [
        provideRouter([]),
        { provide: AccountStore, useValue: accountStore },
        { provide: MarketDataService, useValue: marketData },
      ],
    }).compileComponents();
  });

  it('shows each tradable symbol with its price and day change, green when up and red when down', fakeAsync(() => {
    const [apple, microsoft, ...rest] = items(render());

    expect(rest).toEqual([]);
    expect(apple.querySelector('.symbol')?.textContent).toBe('AAPL');
    expect(apple.querySelector('.price')?.textContent).toBe('231.40');
    expect(apple.querySelector('.change')?.textContent).toBe('▲ 1.20%');
    expect(apple.classList).toContain('up');
    expect(microsoft.querySelector('.change')?.textContent).toBe('▼ 0.40%');
    expect(microsoft.classList).toContain('down');
    expect(marketData.getQuotes).toHaveBeenCalledOnceWith(['AAPL', 'MSFT']);
    discardPeriodicTasks();
  }));

  it('links each symbol to its trade page and reads it out in words', fakeAsync(() => {
    const [apple] = items(render());

    expect(apple.getAttribute('href')).toBe('/trade/AAPL');
    expect(apple.getAttribute('aria-label')).toBe('AAPL 231.40, up 1.20% today. Open the trade page.');
    discardPeriodicTasks();
  }));

  it('draws a sparkline from each symbol’s 15-minute closes', fakeAsync(() => {
    const [apple] = items(render());

    expect(marketData.getBars).toHaveBeenCalledWith('AAPL', '15Min', TickerTapeComponent.SPARKLINE_BARS);
    expect(apple.querySelector('polyline')?.getAttribute('points')).toBe('0.0,16.0 24.0,8.0 48.0,0.0');
    discardPeriodicTasks();
  }));

  it('still shows the price when a symbol’s bars fail to load', fakeAsync(() => {
    marketData.getBars.and.returnValue(throwError(() => new Error('MKT-503')));
    const [apple] = items(render());

    expect(apple.querySelector('.price')?.textContent).toBe('231.40');
    expect(apple.querySelector('svg')).toBeNull();
    discardPeriodicTasks();
  }));

  it('shows a dash for a symbol with no live price', fakeAsync(() => {
    marketData.getQuotes.and.returnValue(of([quote('AAPL', null, null, null), quote('MSFT', 410, 0, 0)]));
    const [apple, microsoft] = items(render());

    expect(apple.querySelector('.price')?.textContent).toBe('—');
    expect(apple.querySelector('.change')).toBeNull();
    expect(apple.getAttribute('aria-label')).toBe('AAPL, no live price');
    expect(microsoft.querySelector('.change')?.textContent).toBe('■ 0.00%');
    discardPeriodicTasks();
  }));

  it('lists held symbols first', fakeAsync(() => {
    heldSymbols.set(['MSFT']);
    const symbols = items(render()).map((item) => item.querySelector('.symbol')?.textContent);

    expect(symbols).toEqual(['MSFT', 'AAPL']);
    discardPeriodicTasks();
  }));

  it('hides the looping copy from screen readers and the keyboard', fakeAsync(() => {
    const element = render();
    const copy = element.querySelectorAll('.group')[1];

    expect(copy.getAttribute('aria-hidden')).toBe('true');
    expect(Array.from(copy.querySelectorAll('a')).every((link) => link.getAttribute('tabindex') === '-1')).toBeTrue();
    discardPeriodicTasks();
  }));

  it('says when prices cannot be loaded', fakeAsync(() => {
    marketData.getQuotes.and.returnValue(throwError(() => new Error('down')));
    const element = render();

    expect(element.querySelector('.status')?.textContent).toContain('Prices are unavailable');
    expect(element.querySelector('.item')).toBeNull();
    discardPeriodicTasks();
  }));

  it('is not shown when there are no instruments to quote', fakeAsync(() => {
    instruments.set([]);
    const element = render();

    expect(element.querySelector('.tape')).toBeNull();
    expect(marketData.getQuotes).not.toHaveBeenCalled();
    discardPeriodicTasks();
  }));
});
