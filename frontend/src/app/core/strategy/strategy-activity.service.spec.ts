/// <reference types="jasmine" />

import { TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { SignalResponse } from '../../api/strategy';
import { ToastService } from '../notifications/toast.service';
import { StrategyActivityService, describeSignal } from './strategy-activity.service';
import { StrategyLabService } from './strategy-lab.service';

const BOUGHT: SignalResponse = {
  id: 1,
  strategyName: 'MA 20/50',
  symbol: 'SPY',
  side: 'BUY',
  price: 580.12,
  outcome: 'PLACED',
};
const REFUSED: SignalResponse = {
  id: 2,
  strategyName: 'MA 20/50',
  symbol: 'SPY',
  side: 'SELL',
  outcome: 'FAILED',
  detail: 'ACC-403 The account is not active',
};

describe('StrategyActivityService', () => {
  let strategyLab: jasmine.SpyObj<StrategyLabService>;
  let toasts: jasmine.SpyObj<ToastService>;

  /** Answers each poll with the next reply, then with no signals */
  const replies = (...answers: Observable<SignalResponse[]>[]) =>
    strategyLab.activity.and.callFake(() => answers.shift() ?? of([]));

  beforeEach(() => {
    strategyLab = jasmine.createSpyObj<StrategyLabService>('StrategyLabService', ['activity']);
    toasts = jasmine.createSpyObj<ToastService>('ToastService', ['show']);
    TestBed.configureTestingModule({
      providers: [
        StrategyActivityService,
        { provide: StrategyLabService, useValue: strategyLab },
        { provide: ToastService, useValue: toasts },
      ],
    });
  });

  it('shows a toast for each new signal, oldest first, and each only once', fakeAsync(() => {
    replies(of([REFUSED, BOUGHT]), of([REFUSED, BOUGHT]));
    TestBed.inject(StrategyActivityService).start();

    tick(StrategyActivityService.POLL_MS);
    tick(StrategyActivityService.POLL_MS);

    expect(toasts.show.calls.allArgs()).toEqual([
      ['info', 'MA 20/50 bought SPY at $580.12.'],
      ['error', 'MA 20/50 could not sell SPY: ACC-403 The account is not active'],
    ]);
    discardPeriodicTasks();
  }));

  it('keeps polling after a failed request', fakeAsync(() => {
    replies(throwError(() => new Error('down')), of([BOUGHT]));
    TestBed.inject(StrategyActivityService).start();

    tick(StrategyActivityService.POLL_MS * 2);

    expect(toasts.show).toHaveBeenCalledOnceWith('info', 'MA 20/50 bought SPY at $580.12.');
    discardPeriodicTasks();
  }));

  it('describes a sale and a refusal without details', () => {
    expect(describeSignal({ ...BOUGHT, side: 'SELL', price: undefined })).toBe('MA 20/50 sold SPY.');
    expect(describeSignal({ ...REFUSED, side: 'BUY', detail: undefined })).toBe(
      'MA 20/50 could not buy SPY: the order was refused'
    );
  });
});
