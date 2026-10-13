import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

/**
 * The admin analytics dashboard: the Streamlit app in the repo's dashboard/ folder.
 * It is a separate app with its own admin sign-in, so admins sign in there again.
 */
export const ANALYTICS_DASHBOARD_URL = 'http://localhost:8501';

@Injectable({ providedIn: 'root' })
export class AnalyticsDashboardService {
  private readonly document = inject(DOCUMENT);

  /** Leaves the Angular app for the analytics dashboard. */
  open(): void {
    this.document.location.assign(ANALYTICS_DASHBOARD_URL);
  }
}
