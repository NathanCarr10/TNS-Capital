/// <reference types="jasmine" />

import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { MarketDataService } from '../../../core/trading/market-data.service';
import { MarketQuotesStore } from '../../../core/trading/market-quotes.store';
import { InstrumentResponse, MarketQuote } from '../../../api/trading';
import { MarketWatchComponent } from './market-watch.component';

describe('MarketWatchComponent', () => {
  let fixture: ComponentFixture<MarketWatchComponent>;
  let marketDataServiceSpy: jasmine.SpyObj<MarketDataService>;

  const instruments: InstrumentResponse[] = [
    { symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
    { symbol: 'MSFT', name: 'Microsoft Corp.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
    { symbol: 'TSLA', name: 'Tesla Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
  ];

  const quote = (symbol: string, price: number | null, previousClose: number | null): MarketQuote => {
    const change = price != null && previousClose != null ? price - previousClose : null;
    return {
      symbol,
      price,
      previousClose,
      change,
      changePercent: change != null && previousClose ? Number(((change * 100) / previousClose).toFixed(2)) : null,
      tradedAt: price != null ? '2026-10-13T14:30:00Z' : null,
    };
  };

  const text = (selector: string): string =>
    (fixture.nativeElement.querySelector(selector) as HTMLElement | null)?.textContent?.trim() ?? '';

  const rowFor = (symbol: string): HTMLTableRowElement => {
    const rows = Array.from(fixture.nativeElement.querySelectorAll('tbody tr')) as HTMLTableRowElement[];
    return rows.find((row) => row.textContent?.includes(symbol))!;
  };

  beforeEach(async () => {
    marketDataServiceSpy = jasmine.createSpyObj<MarketDataService>('MarketDataService', ['getQuotes']);

    await TestBed.configureTestingModule({
      imports: [MarketWatchComponent],
      providers: [provideRouter([]), MarketQuotesStore, { provide: MarketDataService, useValue: marketDataServiceSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(MarketWatchComponent);
  });

  const start = (): void => {
    fixture.componentRef.setInput('instruments', instruments);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
  };

  it('shows skeleton rows while instruments are still loading', () => {
    fixture.componentRef.setInput('instruments', null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tr.skeleton-row').length).toBeGreaterThan(0);
    expect(marketDataServiceSpy.getQuotes).not.toHaveBeenCalled();
  });

  it('loads quotes and colours the change green when up and red when down', fakeAsync(() => {
    marketDataServiceSpy.getQuotes.and.returnValue(
      of([quote('AAPL', 190, 180), quote('MSFT', 400, 410), quote('TSLA', 250, 250)])
    );

    start();

    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledOnceWith(['AAPL', 'MSFT', 'TSLA']);
    expect(fixture.nativeElement.querySelector('tr.skeleton-row')).toBeNull();

    const aapl = rowFor('AAPL');
    expect(aapl.textContent).toContain('$190.00');
    expect(aapl.querySelectorAll('td.change-up')).toHaveSize(2);
    expect(aapl.textContent).toContain('+10.00');
    expect(aapl.textContent).toContain('+5.56%');

    const msft = rowFor('MSFT');
    expect(msft.querySelectorAll('td.change-down')).toHaveSize(2);
    expect(msft.textContent).toContain('-10.00');
    expect(msft.textContent).toContain('-2.44%');

    expect(rowFor('TSLA').querySelectorAll('td.change-flat')).toHaveSize(2);

    discardPeriodicTasks();
  }));

  it('marks a row whose price is unavailable', fakeAsync(() => {
    marketDataServiceSpy.getQuotes.and.returnValue(
      of([quote('AAPL', 190, 180), quote('MSFT', null, null), quote('TSLA', 250, null)])
    );

    start();

    expect(rowFor('MSFT').querySelector('.price-unavailable')?.textContent).toContain('Price unavailable');
    expect(rowFor('AAPL').querySelector('.price-unavailable')).toBeNull();
    expect(rowFor('TSLA').querySelectorAll('td.change-unknown')).toHaveSize(2);

    discardPeriodicTasks();
  }));

  it('refreshes the prices every 15 seconds', fakeAsync(() => {
    marketDataServiceSpy.getQuotes.and.returnValue(of([quote('AAPL', 190, 180)]));
    start();
    expect(rowFor('AAPL').textContent).toContain('$190.00');

    marketDataServiceSpy.getQuotes.and.returnValue(of([quote('AAPL', 170, 180)]));
    tick(MarketQuotesStore.REFRESH_INTERVAL_MS - 1);
    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledTimes(1);

    tick(1);
    fixture.detectChanges();

    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledTimes(2);
    expect(rowFor('AAPL').textContent).toContain('$170.00');
    expect(rowFor('AAPL').querySelectorAll('td.change-down')).toHaveSize(2);

    discardPeriodicTasks();
  }));

  it('fetches immediately when Refresh is clicked', fakeAsync(() => {
    marketDataServiceSpy.getQuotes.and.returnValue(of([quote('AAPL', 190, 180)]));
    start();

    (fixture.nativeElement.querySelector('.panel-actions button') as HTMLButtonElement).click();
    tick();

    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledTimes(2);

    discardPeriodicTasks();
  }));

  it('pauses polling while the tab is hidden and resumes when it is visible again', fakeAsync(() => {
    let hidden = false;
    spyOnProperty(document, 'hidden', 'get').and.callFake(() => hidden);
    marketDataServiceSpy.getQuotes.and.returnValue(of([quote('AAPL', 190, 180)]));
    start();

    hidden = true;
    document.dispatchEvent(new Event('visibilitychange'));
    tick(MarketQuotesStore.REFRESH_INTERVAL_MS * 3);
    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledTimes(1);

    hidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    tick();
    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledTimes(2);

    discardPeriodicTasks();
  }));

  it('lists held symbols first, each group ordered by the biggest move up or down', fakeAsync(() => {
    const more: InstrumentResponse[] = [
      ...instruments,
      { symbol: 'NVDA', name: 'NVIDIA Corp.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
      { symbol: 'AMZN', name: 'Amazon.com Inc.', assetClass: 'EQUITY', currency: 'USD', tradable: true },
    ];
    marketDataServiceSpy.getQuotes.and.returnValue(
      of([
        quote('AAPL', 101, 100), // held, +1%
        quote('MSFT', 95, 100), // held, -5%
        quote('TSLA', 110, 100), // +10%
        quote('NVDA', 97, 100), // -3%
        quote('AMZN', null, null), // no price
      ])
    );

    fixture.componentRef.setInput('instruments', more);
    fixture.componentRef.setInput('heldSymbols', ['AAPL', 'MSFT']);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const symbols = Array.from(fixture.nativeElement.querySelectorAll('tbody .symbol') as NodeListOf<HTMLElement>).map(
      (cell) => cell.textContent!.replace('Held', '').trim()
    );
    expect(symbols).toEqual(['MSFT', 'AAPL', 'TSLA', 'NVDA', 'AMZN']);
    expect(rowFor('MSFT').querySelector('.held-tag')).not.toBeNull();
    expect(rowFor('TSLA').querySelector('.held-tag')).toBeNull();

    discardPeriodicTasks();
  }));

  it('does not fetch again when only the holdings change', fakeAsync(() => {
    marketDataServiceSpy.getQuotes.and.returnValue(of([quote('AAPL', 190, 180)]));
    start();

    fixture.componentRef.setInput('heldSymbols', ['TSLA']);
    fixture.detectChanges();
    tick();

    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledTimes(1);

    discardPeriodicTasks();
  }));

  it('sizes the table to show 10 symbols and scrolls the rest', fakeAsync(() => {
    const many = Array.from({ length: 15 }, (_, index) => ({
      symbol: `SYM${String(index).padStart(2, '0')}`,
      name: `Instrument ${index}`,
      assetClass: 'EQUITY',
      currency: 'USD',
      tradable: true,
    }));
    marketDataServiceSpy.getQuotes.and.returnValue(of(many.map((instrument) => quote(instrument.symbol, 100, 100))));
    fixture.componentRef.setInput('instruments', many);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const wrap = fixture.nativeElement.querySelector('.market-table-wrap') as HTMLElement;
    const rows = fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLElement>;
    const wrapBottom = wrap.getBoundingClientRect().bottom;

    expect(wrap.scrollHeight).toBeGreaterThan(wrap.clientHeight);
    expect(rows[9].getBoundingClientRect().bottom).toBeLessThanOrEqual(wrapBottom + 1);
    expect(rows[10].getBoundingClientRect().top).toBeGreaterThanOrEqual(wrapBottom - 2);

    discardPeriodicTasks();
  }));

  it('shows an error banner with a Retry button that reloads the prices', fakeAsync(() => {
    marketDataServiceSpy.getQuotes.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            error: { errorCode: 'MKT-503', message: 'Market data is unavailable right now; try again later' },
          })
      )
    );
    start();

    expect(text('.error-banner')).toContain('Market data is unavailable right now');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();

    marketDataServiceSpy.getQuotes.and.returnValue(of([quote('AAPL', 190, 180)]));
    (fixture.nativeElement.querySelector('.error-banner button') as HTMLButtonElement).click();
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.error-banner')).toBeNull();
    expect(rowFor('AAPL').textContent).toContain('$190.00');

    discardPeriodicTasks();
  }));

  it('keeps the last prices on screen when a refresh fails', fakeAsync(() => {
    marketDataServiceSpy.getQuotes.and.returnValue(of([quote('AAPL', 190, 180)]));
    start();

    marketDataServiceSpy.getQuotes.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    tick(MarketQuotesStore.REFRESH_INTERVAL_MS);
    fixture.detectChanges();

    expect(text('.error-banner')).toContain('Failed to load market data.');
    expect(rowFor('AAPL').textContent).toContain('$190.00');

    discardPeriodicTasks();
  }));
});
