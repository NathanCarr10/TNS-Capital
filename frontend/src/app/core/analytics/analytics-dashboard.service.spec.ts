import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';

import { AnalyticsDashboardService } from './analytics-dashboard.service';

describe('AnalyticsDashboardService', () => {
  function serviceAt(hostname: string, assign = jasmine.createSpy('assign')) {
    TestBed.configureTestingModule({
      providers: [{ provide: DOCUMENT, useValue: { location: { protocol: 'http:', hostname, assign } } }],
    });
    return TestBed.inject(AnalyticsDashboardService);
  }

  it('sends the browser to the analytics dashboard', () => {
    const assign = jasmine.createSpy('assign');

    serviceAt('localhost', assign).open();

    expect(assign).toHaveBeenCalledWith('http://localhost:8501');
  });

  it('uses the host the app was opened on, so it works from another machine', () => {
    expect(serviceAt('10.9.73.140').url).toBe('http://10.9.73.140:8501');
  });
});
