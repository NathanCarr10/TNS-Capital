import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { startWith } from 'rxjs/operators';

import { BacktestRequest, BacktestResult, CreateStrategyRequest, PerformanceResponse, StrategyResponse } from '../../api/strategy';
import { ToastService } from '../../core/notifications/toast.service';
import { RuleType, StrategyLabService } from '../../core/strategy/strategy-lab.service';
import { AccountStore } from '../../core/trading/account.store';
import { readErrorMessage } from '../../core/trading/api-error';
import { STRATEGY_NAME_PATTERN } from '../../core/validation/input-patterns';
import { EquityCurveChartComponent } from './equity-curve/equity-curve-chart.component';
import { ReplayPanelComponent } from './replay-panel/replay-panel.component';
import { StrategyDetailComponent } from './strategy-detail/strategy-detail.component';

/** The rules a trader can pick, with their settings and defaults (as the strategy service's RuleFactory) */
export const RULES: { type: RuleType; label: string; params: { key: string; label: string; min: number; max: number; step: number; value: number }[] }[] = [
  {
    type: 'MA_CROSSOVER',
    label: 'Moving-average crossover',
    params: [
      { key: 'shortWindow', label: 'Short average (bars)', min: 2, max: 199, step: 1, value: 20 },
      { key: 'longWindow', label: 'Long average (bars)', min: 3, max: 200, step: 1, value: 50 },
    ],
  },
  {
    type: 'BOLLINGER',
    label: 'Bollinger bands',
    params: [
      { key: 'window', label: 'Average (bars)', min: 5, max: 200, step: 1, value: 20 },
      { key: 'width', label: 'Band width (std. dev.)', min: 0.5, max: 4, step: 0.5, value: 2 },
    ],
  },
];

/**
 * Strategy Lab: backtest a trading rule on five years of daily prices, save it
 * as a strategy, switch it on and watch it trade. Support staff see every strategy and
 * can backtest, but cannot create or change strategies.
 */
@Component({
  selector: 'app-strategy-lab',
  imports: [CommonModule, ReactiveFormsModule, EquityCurveChartComponent, StrategyDetailComponent, ReplayPanelComponent],
  templateUrl: './strategy-lab.component.html',
  styleUrl: './strategy-lab.component.scss',
})
export class StrategyLabComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly strategyLab = inject(StrategyLabService);
  private readonly store = inject(AccountStore);
  private readonly toasts = inject(ToastService);

  readonly isSupport = this.store.isSupport;
  readonly instruments = this.store.tradableInstruments;
  readonly rules = RULES;

  readonly form = this.formBuilder.nonNullable.group({
    name: ['MA 20/50', [Validators.required, Validators.maxLength(80), Validators.pattern(STRATEGY_NAME_PATTERN)]],
    symbol: ['SPY', [Validators.required]],
    ruleType: this.formBuilder.nonNullable.control<RuleType>('MA_CROSSOVER'),
    shortWindow: [20, [Validators.required, Validators.min(2), Validators.max(199)]],
    longWindow: [50, [Validators.required, Validators.min(3), Validators.max(200)]],
    window: [20, [Validators.required, Validators.min(5), Validators.max(200)]],
    width: [2, [Validators.required, Validators.min(0.5), Validators.max(4)]],
    quantity: [1, [Validators.required, Validators.min(1), Validators.max(10000)]],
  });

  private readonly ruleType = toSignal(
    this.form.controls.ruleType.valueChanges.pipe(startWith(this.form.controls.ruleType.value)),
    { requireSync: true }
  );
  readonly rule = computed(() => RULES.find((rule) => rule.type === this.ruleType())!);
  readonly formSymbol = toSignal(
    this.form.controls.symbol.valueChanges.pipe(startWith(this.form.controls.symbol.value)),
    { requireSync: true }
  );

  readonly backtest = signal<BacktestResult | null>(null);
  readonly backtesting = signal(false);
  readonly backtestError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);

  readonly strategies = signal<StrategyResponse[]>([]);
  readonly strategiesLoading = signal(true);
  readonly strategiesError = signal<string | null>(null);
  readonly selectedId = signal<number | null>(null);
  readonly selected = computed(() => this.strategies().find((strategy) => strategy.id === this.selectedId()) ?? null);
  readonly busyId = signal<number | null>(null);
  /** The strategies' trading against the trader's own; support staff have no account */
  readonly performance = signal<PerformanceResponse | null>(null);

  constructor() {
    this.loadStrategies();
    this.loadPerformance();
  }

  loadPerformance(): void {
    if (this.isSupport) {
      return;
    }
    this.strategyLab.performance().subscribe({
      next: (performance) => this.performance.set(performance),
      error: () => this.performance.set(null),
    });
  }

  loadStrategies(): void {
    this.strategyLab.list().subscribe({
      next: (strategies) => {
        this.strategies.set(strategies);
        this.strategiesLoading.set(false);
        this.strategiesError.set(null);
        if (this.selectedId() == null && strategies.length > 0) {
          this.selectedId.set((strategies.find((strategy) => strategy.status === 'ACTIVE') ?? strategies[0]).id!);
        }
      },
      error: (error: unknown) => {
        this.strategiesLoading.set(false);
        this.strategiesError.set(readErrorMessage(error, 'Strategies could not be loaded.'));
      },
    });
  }

  runBacktest(): void {
    if (this.invalidRule()) {
      return;
    }
    const value = this.form.getRawValue();
    const request: BacktestRequest = { symbol: value.symbol, ruleType: value.ruleType, params: this.params() };
    this.backtesting.set(true);
    this.backtestError.set(null);
    this.strategyLab.backtest(request).subscribe({
      next: (result) => {
        this.backtesting.set(false);
        this.backtest.set(result);
      },
      error: (error: unknown) => {
        this.backtesting.set(false);
        this.backtest.set(null);
        this.backtestError.set(readErrorMessage(error, 'The backtest could not be run.'));
      },
    });
  }

  save(): void {
    if (this.isSupport || this.invalidRule() || this.form.controls.name.invalid || this.form.controls.quantity.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: CreateStrategyRequest = {
      name: value.name.trim(),
      symbol: value.symbol,
      ruleType: value.ruleType,
      params: this.params(),
      quantity: value.quantity,
    };
    this.saving.set(true);
    this.formError.set(null);
    this.strategyLab.create(request).subscribe({
      next: (created) => {
        this.saving.set(false);
        this.strategies.update((strategies) => [created, ...strategies]);
        this.selectedId.set(created.id!);
        this.toasts.success(`${created.name} saved. Switch it on to start trading.`);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.formError.set(readErrorMessage(error, 'The strategy could not be saved.'));
      },
    });
  }

  /** Switches a strategy on or off; only one per account can be on. */
  toggle(strategy: StrategyResponse): void {
    if (this.isSupport || this.busyId() != null) {
      return;
    }
    const next = strategy.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';
    this.busyId.set(strategy.id!);
    this.strategyLab.setStatus(strategy.id!, next).subscribe({
      next: (updated) => {
        this.busyId.set(null);
        this.replace(updated);
        this.toasts.success(next === 'ACTIVE' ? `${updated.name} is on: it trades ${updated.symbol} on each 1-minute bar.` : `${updated.name} is off.`);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.toasts.error(readErrorMessage(error, `${strategy.name} could not be switched ${next === 'ACTIVE' ? 'on' : 'off'}.`));
      },
    });
  }

  remove(strategy: StrategyResponse): void {
    if (this.isSupport || this.busyId() != null) {
      return;
    }
    this.busyId.set(strategy.id!);
    this.strategyLab.remove(strategy.id!).subscribe({
      next: () => {
        this.busyId.set(null);
        this.strategies.update((strategies) => strategies.filter((item) => item.id !== strategy.id));
        if (this.selectedId() === strategy.id) {
          this.selectedId.set(this.strategies()[0]?.id ?? null);
        }
        this.toasts.info(`${strategy.name} deleted.`);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.toasts.error(readErrorMessage(error, `${strategy.name} could not be deleted.`));
      },
    });
  }

  replace(updated: StrategyResponse): void {
    this.strategies.update((strategies) => strategies.map((item) => (item.id === updated.id ? updated : item)));
  }

  winRate(result: BacktestResult): number | null {
    return result.roundTrips ? ((result.winningRoundTrips ?? 0) * 100) / result.roundTrips : null;
  }

  ruleLabel(type: StrategyResponse['ruleType']): string {
    return RULES.find((rule) => rule.type === type)?.label ?? String(type);
  }

  private params(): Record<string, number> {
    const value = this.form.getRawValue() as unknown as Record<string, number>;
    return Object.fromEntries(this.rule().params.map((param) => [param.key, Number(value[param.key])]));
  }

  private invalidRule(): boolean {
    const controls = this.form.controls as unknown as Record<string, { invalid: boolean; markAsTouched(): void }>;
    const invalid = this.rule().params.filter((param) => controls[param.key].invalid);
    invalid.forEach((param) => controls[param.key].markAsTouched());
    const crossed = this.ruleType() === 'MA_CROSSOVER' && this.form.controls.shortWindow.value >= this.form.controls.longWindow.value;
    this.formError.set(crossed ? 'The short average must be shorter than the long one.' : null);
    return invalid.length > 0 || crossed || this.form.controls.symbol.invalid;
  }
}
