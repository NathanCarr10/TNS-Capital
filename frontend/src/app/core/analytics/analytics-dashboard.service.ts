import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

const ANALYTICS_DASHBOARD_PORT = 8501;

/**
 * The admin analytics dashboard: the Streamlit app in the repo's dashboard/ folder.
 * It is a separate app with its own admin sign-in, so admins sign in there again.
 * It runs on the same machine as this app, so it is reached on whatever host the
 * browser used for this page (localhost, or the machine's IP from another computer).
 */
export function analyticsDashboardUrl(location: Pick<Location, 'protocol' | 'hostname'>): string {
  return `${location.protocol}//${location.hostname}:${ANALYTICS_DASHBOARD_PORT}`;
}

@Injectable({ providedIn: 'root' })
export class AnalyticsDashboardService {
  private readonly document = inject(DOCUMENT);

  readonly url = analyticsDashboardUrl(this.document.location);

  /** Leaves the Angular app for the analytics dashboard. */
  open(): void {
    this.document.location.assign(this.url);
  }
}
