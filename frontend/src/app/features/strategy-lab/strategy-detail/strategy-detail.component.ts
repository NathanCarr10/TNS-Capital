import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, forkJoin, timer } from 'rxjs';
import { catchError, distinctUntilChanged, map, switchMap } from 'rxjs/operators';

import { SeriesView, SignalResponse, StrategyResponse } from '../../../api/strategy';
import { StrategyLabService } from '../../../core/strategy/strategy-lab.service';
import { readErrorMessage } from '../../../core/trading/api-error';

/** A strategy's live state and what it has done, refreshed every few seconds. */
@Component({
  selector: 'app-strategy-detail',
  imports: [CommonModule],
  templateUrl: './strategy-detail.component.html',
  styleUrl: './strategy-detail.component.scss',
})
export class StrategyDetailComponent {
  static readonly REFRESH_MS = 5000;

  private readonly strategyLab = inject(StrategyLabService);

  readonly strategy = input.required<StrategyResponse>();
  /** The strategy as the service now has it, e.g. its holding after an order */
  readonly changed = output<StrategyResponse>();

  readonly signals = signal<SignalResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /** What the service holds of the symbol's 1-minute bars, live and replayed */
  readonly series = computed(() => this.strategy().market ?? []);

  constructor() {
    toObservable(this.strategy)
      .pipe(
        map((strategy) => strategy.id!),
        distinctUntilChanged(),
        switchMap((id) => {
          this.loading.set(true);
          this.signals.set([]);
          return timer(0, StrategyDetailComponent.REFRESH_MS).pipe(
            switchMap(() =>
              forkJoin({ strategy: this.strategyLab.get(id), signals: this.strategyLab.signals(id) }).pipe(
                catchError((error: unknown) => {
                  this.loading.set(false);
                  this.error.set(readErrorMessage(error, 'The strategy could not be refreshed.'));
                  return EMPTY;
                })
              )
            )
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe(({ strategy, signals }) => {
        this.loading.set(false);
        this.error.set(null);
        this.signals.set(signals);
        this.changed.emit(strategy);
      });
  }

  /** e.g. "12 of 51 bars": the rule signals once it has barsNeeded bars */
  warmUp(view: SeriesView): string {
    const needed = this.strategy().barsNeeded ?? 0;
    const collected = view.barsCollected ?? 0;
    return collected >= needed ? 'Ready' : `Warming up: ${collected} of ${needed} bars`;
  }

  progress(view: SeriesView): number {
    const needed = this.strategy().barsNeeded ?? 1;
    return Math.min(100, Math.round(((view.barsCollected ?? 0) * 100) / needed));
  }
}
