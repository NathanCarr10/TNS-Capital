/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { PositionResponse } from '../../../api/trading';
import { MarketDataService } from '../../../core/trading/market-data.service';
import { PriceBar } from '../../../core/trading/trading.models';
import { PortfolioValueChartComponent } from './portfolio-value-chart.component';

describe('PortfolioValueChartComponent', () => {
  let fixture: ComponentFixture<PortfolioValueChartComponent>;
  let marketData: jasmine.SpyObj<MarketDataService>;

  const positions: PositionResponse[] = [{ accountId: 11, symbol: 'AAPL', quantity: 10, reservedQuantity: 0, availableQuantity: 10, averageCost: 100, marketValue: null }];
  const bar = (day: number, close: number): PriceBar => ({
    time: `2026-10-0${day}T04:00:00Z`,
    open: close,
    high: close,
    low: close,
    close,
    volume: 1,
  });

  const render = (held: PositionResponse[] | null, cash = 1000): HTMLElement => {
    fixture.componentRef.setInput('positions', held);
    fixture.componentRef.setInput('cashBalance', cash);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  beforeEach(async () => {
    marketData = jasmine.createSpyObj<MarketDataService>('MarketDataService', ['getBars']);
    marketData.getBars.and.returnValue(of([bar(1, 100), bar(2, 110)]));
    await TestBed.configureTestingModule({
      imports: [PortfolioValueChartComponent],
      providers: [{ provide: MarketDataService, useValue: marketData }],
    }).compileComponents();
    fixture = TestBed.createComponent(PortfolioValueChartComponent);
  });

  it('shows the latest value and the change over the range', () => {
    const element = render(positions);

    expect(marketData.getBars).toHaveBeenCalledWith('AAPL', '1Day', 22);
    // 1000 cash + 10 x 110, up from 1000 + 10 x 100
    expect(element.querySelector('.headline strong')?.textContent).toContain('$2,100.00');
    expect(element.querySelector('.headline .pnl-up')?.textContent).toContain('+$100.00');
    expect(element.querySelector('.headline .pnl-up')?.textContent).toContain('(+5.00%)');
  });

  it('refetches when the range changes', () => {
    const element = render(positions);

    Array.from(element.querySelectorAll('.segmented button') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.textContent?.trim() === '1Y')!
      .click();
    fixture.detectChanges();

    expect(marketData.getBars).toHaveBeenCalledWith('AAPL', '1Day', 252);
  });

  it('says so when there are no holdings', () => {
    const element = render([]);

    expect(element.querySelector('.note')?.textContent).toContain('No holdings yet');
    expect(marketData.getBars).not.toHaveBeenCalled();
  });

  it('says so when no price history can be loaded', () => {
    marketData.getBars.and.returnValue(throwError(() => new Error('down')));

    const element = render(positions);

    expect(element.querySelector('.note')?.textContent).toContain('unavailable');
  });
});
