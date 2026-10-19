/// <reference types="jasmine" />

import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { ReplayStatus } from '../../../api/trading';
import { StrategyLabService } from '../../../core/strategy/strategy-lab.service';
import { ReplayPanelComponent, lastWeekday } from './replay-panel.component';

const RUNNING: ReplayStatus = {
  replayId: 'r-1',
  symbol: 'SPY',
  date: '2026-10-16',
  barsPublished: 39,
  totalBars: 390,
  barsPerSecond: 10,
  running: true,
};

describe('ReplayPanelComponent', () => {
  let fixture: ComponentFixture<ReplayPanelComponent>;
  let strategyLab: jasmine.SpyObj<StrategyLabService>;

  const create = (current: ReplayStatus | null): HTMLElement => {
    strategyLab.currentReplay.and.returnValue(of(current));
    fixture = TestBed.createComponent(ReplayPanelComponent);
    fixture.componentRef.setInput('symbol', 'SPY');
    fixture.detectChanges();
    // The first poll for the current replay
    tick(0);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  beforeEach(() => {
    strategyLab = jasmine.createSpyObj<StrategyLabService>('StrategyLabService', ['currentReplay', 'startReplay', 'stopReplay']);
    TestBed.configureTestingModule({
      imports: [ReplayPanelComponent],
      providers: [{ provide: StrategyLabService, useValue: strategyLab }],
    });
  });

  it('starts a replay of the chosen day for the symbol', fakeAsync(() => {
    const element = create(null);
    strategyLab.startReplay.and.returnValue(of(RUNNING));
    fixture.componentInstance.form.setValue({ date: '2026-10-16', barsPerSecond: 25 });

    (element.querySelector('button[type=submit]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(strategyLab.startReplay).toHaveBeenCalledOnceWith({ symbol: 'SPY', date: '2026-10-16', barsPerSecond: 25 });
    expect(element.querySelector('.status')?.textContent).toContain('39 of 390 bars');
    expect(fixture.componentInstance.progress()).toBe(10);
    discardPeriodicTasks();
  }));

  it('stops a running replay', fakeAsync(() => {
    const element = create(RUNNING);
    strategyLab.stopReplay.and.returnValue(of({ ...RUNNING, running: false }));

    (element.querySelector('button[type=button]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(strategyLab.stopReplay).toHaveBeenCalled();
    expect(element.querySelector('.status')?.textContent).toContain('Last replay');
    discardPeriodicTasks();
  }));

  it("shows the API's reason when a replay cannot start", fakeAsync(() => {
    const element = create(null);
    strategyLab.startReplay.and.returnValue(
      throwError(() => ({ error: { errorCode: 'MKT-404', message: 'No 1-minute bars for SPY on 2026-10-11' } }))
    );

    fixture.componentInstance.start();
    fixture.detectChanges();

    expect(element.querySelector('[role=alert]')?.textContent).toContain('No 1-minute bars for SPY');
    discardPeriodicTasks();
  }));

  it('suggests the last weekday', () => {
    expect(lastWeekday(new Date(2026, 9, 19))).toBe('2026-10-16');
    expect(lastWeekday(new Date(2026, 9, 21))).toBe('2026-10-20');
  });
});
