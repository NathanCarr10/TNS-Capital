import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';

import { ANALYTICS_DASHBOARD_URL, AnalyticsDashboardService } from './analytics-dashboard.service';

describe('AnalyticsDashboardService', () => {
  it('sends the browser to the analytics dashboard', () => {
    const assign = jasmine.createSpy('assign');
    TestBed.configureTestingModule({
      providers: [{ provide: DOCUMENT, useValue: { location: { assign } } }],
    });

    TestBed.inject(AnalyticsDashboardService).open();

    expect(assign).toHaveBeenCalledWith(ANALYTICS_DASHBOARD_URL);
  });
});
