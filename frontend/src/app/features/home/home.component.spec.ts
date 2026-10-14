/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ANALYTICS_DASHBOARD_URL } from '../../core/analytics/analytics-dashboard.service';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
  });

  it('links traders to the login page', () => {
    const traderLink: HTMLAnchorElement = fixture.nativeElement.querySelector('a.trader-entry');

    expect(traderLink.getAttribute('href')).toBe('/login');
  });

  it('links admins to the analytics dashboard', () => {
    const adminLink: HTMLAnchorElement = fixture.nativeElement.querySelector('.admin-access a');

    expect(adminLink.getAttribute('href')).toBe(ANALYTICS_DASHBOARD_URL);
    expect(adminLink.textContent?.trim()).toBe('Open admin analytics');
  });
});
