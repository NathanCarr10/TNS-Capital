/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AnalyticsDashboardService } from '../../core/analytics/analytics-dashboard.service';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let analyticsSpy: jasmine.SpyObj<AnalyticsDashboardService>;

  beforeEach(async () => {
    analyticsSpy = jasmine.createSpyObj<AnalyticsDashboardService>('AnalyticsDashboardService', ['open']);

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideRouter([]), { provide: AnalyticsDashboardService, useValue: analyticsSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
  });

  it('links traders to the login page', () => {
    const traderLink: HTMLAnchorElement = fixture.nativeElement.querySelector('a.home-option');

    expect(traderLink.getAttribute('href')).toBe('/login');
  });

  it('sends admins to the analytics dashboard', () => {
    const adminButton: HTMLButtonElement = fixture.nativeElement.querySelector('button.home-option');

    adminButton.click();

    expect(analyticsSpy.open).toHaveBeenCalled();
  });
});
