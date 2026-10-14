import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AnalyticsDashboardService } from '../../core/analytics/analytics-dashboard.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  private readonly analyticsDashboard = inject(AnalyticsDashboardService);

  // The analytics dashboard has its own admin sign-in, so admins go straight there
  openAdminDashboard(): void {
    this.analyticsDashboard.open();
  }
}
