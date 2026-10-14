/// <reference types="jasmine" />

import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MarketQuotesStore } from '../../../core/trading/market-quotes.store';
import { MarketQuote, PositionResponse } from '../../../api/trading';
import { HoldingsComponent } from './holdings.component';

describe('HoldingsComponent', () => {
  let fixture: ComponentFixture<HoldingsComponent>;
  let store: Pick<MarketQuotesStore, 'quotes' | 'lastUpdated' | 'errorMessage'>;

  const positions: PositionResponse[] = [
    { accountId: 11, symbol: 'AAPL', quantity: 10, averageCost: 100, marketValue: null },
    { accountId: 11, symbol: 'MSFT', quantity: 5, averageCost: 200, marketValue: null },
    { accountId: 11, symbol: 'TSLA', quantity: 2, averageCost: 250, marketValue: null },
  ];

  const quote = (symbol: string, price: number | null): MarketQuote => ({
    symbol,
    price,
    previousClose: null,
    change: null,
    changePercent: null,
    tradedAt: null,
  });

  const render = (inputs: { positions: PositionResponse[] | null; error?: string }) => {
    fixture.componentRef.setInput('positions', inputs.positions);
    fixture.componentRef.setInput('error', inputs.error ?? null);
    fixture.detectChanges();
  };

  const setQuotes = (...quotes: MarketQuote[]) => {
    store.quotes.set(new Map(quotes.map((q) => [q.symbol, q])));
    store.lastUpdated.set(new Date());
    fixture.detectChanges();
  };

  const rowFor = (symbol: string): HTMLTableRowElement => {
    const rows = Array.from(fixture.nativeElement.querySelectorAll('tbody tr')) as HTMLTableRowElement[];
    return rows.find((row) => row.querySelector('.symbol')?.textContent?.trim() === symbol)!;
  };

  beforeEach(async () => {
    store = { quotes: signal(new Map()), lastUpdated: signal(null), errorMessage: signal(null) };

    await TestBed.configureTestingModule({
      imports: [HoldingsComponent],
      providers: [provideRouter([]), { provide: MarketQuotesStore, useValue: store }],
    }).compileComponents();

    fixture = TestBed.createComponent(HoldingsComponent);
  });

  it('shows skeleton rows until positions and the first quotes arrive', () => {
    render({ positions: null });
    expect(fixture.nativeElement.querySelectorAll('tr.skeleton-row').length).toBeGreaterThan(0);

    render({ positions });
    expect(fixture.nativeElement.querySelectorAll('tr.skeleton-row').length).toBeGreaterThan(0);

    setQuotes(quote('AAPL', 120), quote('MSFT', 190), quote('TSLA', 250));
    expect(fixture.nativeElement.querySelector('tr.skeleton-row')).toBeNull();
  });

  it('lists each holding with its quantity, value, average price and unrealised return', () => {
    render({ positions });
    setQuotes(quote('AAPL', 120), quote('MSFT', 190), quote('TSLA', 250));

    const cells = (symbol: string) =>
      Array.from(rowFor(symbol).querySelectorAll('td')).map((cell) => cell.textContent!.replace(/\s+/g, ' ').trim());

    expect(cells('AAPL')).toEqual(['AAPL', '10', '$1,200.00', '$100.00', '+$200.00 +20.00%']);
    expect(rowFor('AAPL').querySelector('td.return')!.classList).toContain('pnl-up');

    expect(cells('MSFT')).toEqual(['MSFT', '5', '$950.00', '$200.00', '-$50.00 -5.00%']);
    expect(rowFor('MSFT').querySelector('td.return')!.classList).toContain('pnl-down');

    expect(rowFor('TSLA').querySelector('td.return')!.classList).toContain('pnl-flat');
  });

  it('orders holdings by value, largest first, with unpriced ones last', () => {
    render({ positions });
    setQuotes(quote('AAPL', 120), quote('MSFT', 300), quote('TSLA', null));

    const symbols = Array.from(fixture.nativeElement.querySelectorAll('tbody .symbol') as NodeListOf<HTMLElement>).map(
      (cell) => cell.textContent!.trim()
    );
    expect(symbols).toEqual(['MSFT', 'AAPL', 'TSLA']);
  });

  it('marks a holding without a live price instead of valuing it at zero', () => {
    render({ positions });
    setQuotes(quote('AAPL', 120), quote('MSFT', null));

    const msft = rowFor('MSFT');
    expect(msft.querySelector('.price-unavailable')?.textContent).toContain('Price unavailable');
    expect(msft.querySelector('td.return')!.textContent).toContain('—');
    expect(msft.textContent).toContain('$200.00');
  });

  it('shows positions with prices unavailable when market data fails', () => {
    render({ positions });
    store.errorMessage.set('Failed to load market data.');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('tr.skeleton-row')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.price-unavailable').length).toBe(3);
  });

  it('shows an empty state when there are no open positions', () => {
    render({ positions: [] });

    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    expect(fixture.nativeElement.querySelector('.note')?.textContent).toContain('No open positions yet');
  });

  it('shows the error when positions could not be loaded', () => {
    render({ positions: [], error: 'Failed to load positions.' });

    expect(fixture.nativeElement.querySelector('.note.error')?.textContent).toContain('Failed to load positions.');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });
});
