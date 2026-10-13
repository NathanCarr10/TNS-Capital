/// <reference types="jasmine" />

import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { MarketDataService } from '../market-data.service';
import { InstrumentResponse, MarketQuote } from '../trading.models';
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
      providers: [{ provide: MarketDataService, useValue: marketDataServiceSpy }],
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
    expect(aapl.querySelectorAll('td.change-up').length).toBe(2);
    expect(aapl.textContent).toContain('+10.00');
    expect(aapl.textContent).toContain('+5.56%');

    const msft = rowFor('MSFT');
    expect(msft.querySelectorAll('td.change-down').length).toBe(2);
    expect(msft.textContent).toContain('-10.00');
    expect(msft.textContent).toContain('-2.44%');

    expect(rowFor('TSLA').querySelectorAll('td.change-flat').length).toBe(2);

    discardPeriodicTasks();
  }));

  it('marks a row whose price is unavailable', fakeAsync(() => {
    marketDataServiceSpy.getQuotes.and.returnValue(
      of([quote('AAPL', 190, 180), quote('MSFT', null, null), quote('TSLA', 250, null)])
    );

    start();

    expect(rowFor('MSFT').querySelector('.price-unavailable')?.textContent).toContain('Price unavailable');
    expect(rowFor('AAPL').querySelector('.price-unavailable')).toBeNull();
    expect(rowFor('TSLA').querySelectorAll('td.change-unknown').length).toBe(2);

    discardPeriodicTasks();
  }));

  it('refreshes the prices every 15 seconds', fakeAsync(() => {
    marketDataServiceSpy.getQuotes.and.returnValue(of([quote('AAPL', 190, 180)]));
    start();
    expect(rowFor('AAPL').textContent).toContain('$190.00');

    marketDataServiceSpy.getQuotes.and.returnValue(of([quote('AAPL', 170, 180)]));
    tick(MarketWatchComponent.REFRESH_INTERVAL_MS - 1);
    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledTimes(1);

    tick(1);
    fixture.detectChanges();

    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledTimes(2);
    expect(rowFor('AAPL').textContent).toContain('$170.00');
    expect(rowFor('AAPL').querySelectorAll('td.change-down').length).toBe(2);

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
    tick(MarketWatchComponent.REFRESH_INTERVAL_MS * 3);
    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledTimes(1);

    hidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    tick();
    expect(marketDataServiceSpy.getQuotes).toHaveBeenCalledTimes(2);

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
    tick(MarketWatchComponent.REFRESH_INTERVAL_MS);
    fixture.detectChanges();

    expect(text('.error-banner')).toContain('Failed to load market data.');
    expect(rowFor('AAPL').textContent).toContain('$190.00');

    discardPeriodicTasks();
  }));
});
