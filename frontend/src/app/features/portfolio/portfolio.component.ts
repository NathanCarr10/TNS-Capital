import { CommonModule } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AnalyticsDashboardService } from '../../core/analytics/analytics-dashboard.service';
import { AccountStore } from '../../core/trading/account.store';
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

  /** First name of the account holder, for the greeting. */
  readonly firstName = computed(() => this.account()?.holderName.split(/\s+/)[0] ?? '');

  viewAccount(accountId: number): void {
    this.store.viewAccount(accountId);
  }

  openAnalytics(): void {
    this.analyticsDashboard.open();
  }
}
