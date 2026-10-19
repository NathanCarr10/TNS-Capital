/// <reference types="jasmine" />

import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { SignalResponse, StrategyResponse } from '../../../api/strategy';
import { StrategyLabService } from '../../../core/strategy/strategy-lab.service';
import { StrategyDetailComponent } from './strategy-detail.component';

const STRATEGY: StrategyResponse = {
  id: 4,
  name: 'MA 20/50',
  symbol: 'SPY',
  rule: 'SMA 20 / SMA 50 crossover',
  quantity: 3,
  status: 'ACTIVE',
  position: 'LONG',
  barsNeeded: 51,
  market: [
    { source: 'LIVE', barsCollected: 12, lastClose: 580.12, lastBarTime: '2026-10-19T14:31:00Z' },
    { source: 'REPLAY', barsCollected: 120, lastClose: 575.5, lastBarTime: '2026-10-16T15:30:00Z' },
  ],
};

const SIGNALS: SignalResponse[] = [
  { id: 2, side: 'SELL', price: 581, barTime: '2026-10-16T15:00:00Z', source: 'REPLAY', outcome: 'FAILED', detail: 'ORD-422 Too far from the market' },
  { id: 1, side: 'BUY', price: 579, barTime: '2026-10-16T14:00:00Z', source: 'REPLAY', outcome: 'PLACED', orderStatus: 'FILLED' },
];

describe('StrategyDetailComponent', () => {
  let fixture: ComponentFixture<StrategyDetailComponent>;
  let strategyLab: jasmine.SpyObj<StrategyLabService>;
  let changed: StrategyResponse[];

  beforeEach(() => {
    strategyLab = jasmine.createSpyObj<StrategyLabService>('StrategyLabService', ['get', 'signals']);
    TestBed.configureTestingModule({
      imports: [StrategyDetailComponent],
      providers: [{ provide: StrategyLabService, useValue: strategyLab }],
    });
    changed = [];
  });

  /** Creates it and lets the first refresh run; call in fakeAsync */
  const create = (): HTMLElement => {
    fixture = TestBed.createComponent(StrategyDetailComponent);
    fixture.componentRef.setInput('strategy', STRATEGY);
    fixture.componentInstance.changed.subscribe((strategy) => changed.push(strategy));
    fixture.detectChanges();
    tick(0);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  it('shows how far each price series is from the bars the rule needs', fakeAsync(() => {
    strategyLab.get.and.returnValue(of(STRATEGY));
    strategyLab.signals.and.returnValue(of([]));

    const element = create();

    const rows = Array.from(element.querySelectorAll('.series li')).map((row) => row.textContent);
    expect(rows[0]).toContain('Warming up: 12 of 51 bars');
    expect(rows[1]).toContain('Ready');
    expect(element.querySelector('.card-header .badge')?.textContent).toContain('holding 3 shares');
    expect(element.textContent).toContain('No signals yet');
    expect(changed).toEqual([STRATEGY]);
    discardPeriodicTasks();
  }));

  it('lists the signals with their order status or why they were refused', fakeAsync(() => {
    strategyLab.get.and.returnValue(of({ ...STRATEGY, market: [] }));
    strategyLab.signals.and.returnValue(of(SIGNALS));

    const element = create();

    const rows = Array.from(element.querySelectorAll('tbody tr')).map((row) => row.textContent);
    expect(rows[0]).toContain('Refused');
    expect(rows[0]).toContain('ORD-422 Too far from the market');
    expect(rows[1]).toContain('FILLED');
    expect(rows[1]).toContain('Replay');
    discardPeriodicTasks();
  }));

  it('says when the strategy cannot be refreshed', fakeAsync(() => {
    strategyLab.get.and.returnValue(throwError(() => ({ status: 503 })));
    strategyLab.signals.and.returnValue(of([]));

    const element = create();

    expect(element.querySelector('.note.error')?.textContent).toContain('could not be refreshed');
    discardPeriodicTasks();
  }));
});
