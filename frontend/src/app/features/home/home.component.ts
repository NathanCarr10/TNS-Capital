import { Component, OnDestroy, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ANALYTICS_DASHBOARD_URL } from '../../core/analytics/analytics-dashboard.service';
import { MarketStatus, nyseStatus } from '../../layout/market-status/market-hours';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnDestroy {
  readonly analyticsDashboardUrl = ANALYTICS_DASHBOARD_URL;
  readonly marketStatus = signal<MarketStatus>(nyseStatus(new Date()));

  private readonly marketClock = window.setInterval(() => {
    this.marketStatus.set(nyseStatus(new Date()));
  }, 1000);

  ngOnDestroy(): void {
    window.clearInterval(this.marketClock);
  }
}
