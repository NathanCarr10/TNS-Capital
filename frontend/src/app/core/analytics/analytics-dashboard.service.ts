import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

/**
 * The admin analytics dashboard: the Streamlit app in the repo's dashboard/ folder.
 * It is a separate app with its own admin sign-in, so admins sign in there again.
 * It is served on this app's origin: nginx (and `ng serve`'s proxy) forward /analytics/ to it,
 * so the link works wherever the app is opened from, not only on the Docker host.
 */
export const ANALYTICS_DASHBOARD_URL = '/analytics/';

@Injectable({ providedIn: 'root' })
export class AnalyticsDashboardService {
  private readonly document = inject(DOCUMENT);

  /** Leaves the Angular app for the analytics dashboard. */
  open(): void {
    this.document.location.assign(ANALYTICS_DASHBOARD_URL);
  }
}
