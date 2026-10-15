import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AnalyticsDashboardService } from '../../core/analytics/analytics-dashboard.service';
import { AccountStore } from '../../core/trading/account.store';
import { readErrorMessage } from '../../core/trading/api-error';
import { MarketQuotesStore } from '../../core/trading/market-quotes.store';
import { IconComponent } from '../../layout/icon/icon.component';
import { AdminAccountBarComponent } from './admin-account-bar/admin-account-bar.component';
import { HoldingsComponent } from './holdings/holdings.component';
import { MarketWatchComponent } from './market-watch/market-watch.component';
import { PerformanceSummaryComponent } from './performance-summary/performance-summary.component';
import { PortfolioSplitComponent } from './portfolio-split/portfolio-split.component';
import { PortfolioValueChartComponent } from './portfolio-value/portfolio-value-chart.component';

/** The account at a glance: cash, P&L, value over time, how it is split, holdings and a watchlist. */
@Component({
  selector: 'app-portfolio',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    IconComponent,
    AdminAccountBarComponent,
    HoldingsComponent,
    MarketWatchComponent,
    PerformanceSummaryComponent,
    PortfolioSplitComponent,
    PortfolioValueChartComponent,
  ],
  providers: [MarketQuotesStore],
  templateUrl: './portfolio.component.html',
  styleUrl: './portfolio.component.scss',
})
export class PortfolioComponent {
  private readonly store = inject(AccountStore);
  private readonly analyticsDashboard = inject(AnalyticsDashboardService);
  private readonly destroyRef = inject(DestroyRef);

  readonly today = new Date();
  readonly isAdmin = this.store.isAdmin;
  readonly adminAccounts = this.store.adminAccounts;
  readonly account = this.store.account;
  readonly accountLoading = this.store.accountLoading;
  readonly accountError = this.store.accountError;
  readonly positions = this.store.positions;
  readonly positionsError = this.store.positionsError;
  readonly heldSymbols = this.store.heldSymbols;
  readonly instruments = this.store.tradableInstruments;
  readonly instrumentsLoading = computed(() => this.store.instruments() === null);
  readonly cashBalance = computed(() => this.account()?.cashBalance ?? null);
  readonly depositOpen = signal(false);
  readonly depositLoading = signal(false);
  readonly depositMessage = signal<string | null>(null);
  readonly depositError = signal<string | null>(null);
  readonly statusLoading = signal(false);
  readonly statusError = signal<string | null>(null);
  readonly depositForm = inject(FormBuilder).nonNullable.group({
    amount: [0, [Validators.required, Validators.min(0.01)]],
  });

  /** First name of the account holder, for the greeting. */
  readonly firstName = computed(() => this.account()?.holderName.split(/\s+/)[0] ?? '');

  viewAccount(accountId: number): void {
    this.statusError.set(null);
    this.store.viewAccount(accountId);
  }

  /** Admin view: suspend the account if it is active, otherwise reactivate it. */
  toggleStatus(): void {
    const account = this.account();
    if (!account || this.statusLoading()) {
      return;
    }

    this.statusLoading.set(true);
    this.statusError.set(null);
    this.store
      .setStatus(account.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.statusLoading.set(false),
        error: (error: unknown) => {
          this.statusLoading.set(false);
          // e.g. the account is closed (ACC-403)
          this.statusError.set(readErrorMessage(error, 'Could not change the account status. Please try again.'));
        },
      });
  }

  openAnalytics(): void {
    this.analyticsDashboard.open();
  }

  toggleDeposit(): void {
    this.depositOpen.update((open) => !open);
    this.depositMessage.set(null);
    this.depositError.set(null);
  }

  submitDeposit(): void {
    if (this.depositForm.invalid || this.depositLoading()) {
      this.depositForm.markAllAsTouched();
      return;
    }

    this.depositLoading.set(true);
    this.depositMessage.set(null);
    this.depositError.set(null);
    this.store
      .deposit(this.depositForm.controls.amount.value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.depositLoading.set(false);
          this.depositMessage.set('Cash deposited successfully.');
          this.depositForm.reset({ amount: 0 });
        },
        error: (error: unknown) => {
          this.depositLoading.set(false);
          // e.g. the account is not active (ACC-403) or the amount is invalid (VAL-422)
          this.depositError.set(readErrorMessage(error, 'Could not deposit cash. Please try again.'));
        },
      });
  }
}
