import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EMPTY, timer } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

import { ReplayStatus } from '../../../api/trading';
import { StrategyLabService } from '../../../core/strategy/strategy-lab.service';
import { readErrorMessage } from '../../../core/trading/api-error';

/** The last weekday before today, as yyyy-mm-dd: a likely trading day to replay */
export function lastWeekday(today = new Date()): string {
  const day = new Date(today);
  do {
    day.setDate(day.getDate() - 1);
  } while (day.getDay() === 0 || day.getDay() === 6);
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`;
}

/**
 * Replays a past trading day's 1-minute bars on the price stream at high speed,
 * so switched-on strategies trade outside market hours. One replay runs at a time
 * for everyone; orders are still priced at the live market price.
 */
@Component({
  selector: 'app-replay-panel',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './replay-panel.component.html',
  styleUrl: './replay-panel.component.scss',
})
export class ReplayPanelComponent {
  static readonly REFRESH_MS = 1000;

  private readonly strategyLab = inject(StrategyLabService);
  private readonly formBuilder = inject(FormBuilder);

  readonly symbol = input.required<string>();

  readonly speeds = [5, 10, 25, 50];
  readonly maxDate = lastWeekday();
  readonly form = this.formBuilder.nonNullable.group({
    date: [this.maxDate, [Validators.required]],
    barsPerSecond: [10],
  });

  readonly replay = signal<ReplayStatus | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly running = computed(() => this.replay()?.running ?? false);
  readonly progress = computed(() => {
    const replay = this.replay();
    return replay?.totalBars ? Math.round(((replay.barsPublished ?? 0) * 100) / replay.totalBars) : 0;
  });

  constructor() {
    timer(0, ReplayPanelComponent.REFRESH_MS)
      .pipe(
        switchMap(() => this.strategyLab.currentReplay().pipe(catchError(() => EMPTY))),
        takeUntilDestroyed()
      )
      .subscribe((replay) => this.replay.set(replay));
  }

  start(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    const value = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.strategyLab
      .startReplay({ symbol: this.symbol(), date: value.date, barsPerSecond: Number(value.barsPerSecond) })
      .subscribe({
        next: (replay) => {
          this.busy.set(false);
          this.replay.set(replay);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.error.set(readErrorMessage(error, 'The replay could not be started.'));
        },
      });
  }

  stop(): void {
    this.busy.set(true);
    this.strategyLab.stopReplay().subscribe({
      next: (replay) => {
        this.busy.set(false);
        this.replay.set(replay);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(readErrorMessage(error, 'The replay could not be stopped.'));
      },
    });
  }
}
