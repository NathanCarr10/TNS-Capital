import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ANALYTICS_DASHBOARD_URL } from '../../core/analytics/analytics-dashboard.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  readonly analyticsDashboardUrl = ANALYTICS_DASHBOARD_URL;
}
