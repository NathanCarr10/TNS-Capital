import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, timer } from 'rxjs';
import { catchError, exhaustMap, tap } from 'rxjs/operators';

import { SignalResponse } from '../../api/strategy';
import { ToastService } from '../notifications/toast.service';
import { StrategyLabService } from './strategy-lab.service';

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

/**
 * Tells a trader when one of their strategies trades, on any page: polls the
 * strategy service for new signals and shows a toast for each.
 * Provided by the app shell for traders, so it runs while they are signed in.
 */
@Injectable()
export class StrategyActivityService {
  static readonly POLL_MS = 10_000;
  /** Each poll looks back this far, in case the browser's clock is ahead of the service's */
  static readonly OVERLAP_MS = 60_000;

  private readonly strategyLab = inject(StrategyLabService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private since = new Date();
  // Polls overlap, so a signal can come back; each is announced once
  private readonly seen = new Set<number>();

  start(): void {
    timer(StrategyActivityService.POLL_MS, StrategyActivityService.POLL_MS)
      .pipe(
        exhaustMap(() => {
          const asked = Date.now();
          return this.strategyLab.activity(this.since).pipe(
            tap((signals) => {
              this.since = new Date(asked - StrategyActivityService.OVERLAP_MS);
              this.announce(signals);
            }),
            catchError(() => EMPTY)
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();
  }

  private announce(signals: SignalResponse[]): void {
    [...signals].reverse().forEach((signal) => {
      if (signal.id == null || this.seen.has(signal.id)) {
        return;
      }
      this.seen.add(signal.id);
      this.toasts.show(signal.outcome === 'PLACED' ? 'info' : 'error', describeSignal(signal));
    });
  }
}

/** One line about a signal, e.g. "MA 20/50 bought 3 SPY at $580.12". */
export function describeSignal(signal: SignalResponse): string {
  const verb = signal.side === 'BUY' ? 'bought' : 'sold';
  const price = signal.price == null ? '' : ` at ${currency.format(signal.price)}`;
  if (signal.outcome === 'PLACED') {
    return `${signal.strategyName} ${verb} ${signal.symbol}${price}.`;
  }
  return `${signal.strategyName} could not ${signal.side === 'BUY' ? 'buy' : 'sell'} ${signal.symbol}: ${signal.detail ?? 'the order was refused'}`;
}
