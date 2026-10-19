/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EquityCurveChartComponent } from './equity-curve-chart.component';

describe('EquityCurveChartComponent', () => {
  let fixture: ComponentFixture<EquityCurveChartComponent>;

  beforeEach(() => {
    fixture = TestBed.createComponent(EquityCurveChartComponent);
    fixture.componentRef.setInput('curve', [
      { date: '2024-01-02', strategy: 10000, buyAndHold: 10000 },
      { date: '2024-06-03', strategy: 11000, buyAndHold: 10500 },
      { date: '2024-12-31', strategy: 12500, buyAndHold: 11000 },
    ]);
    fixture.componentInstance.width.set(400);
    fixture.detectChanges();
  });

  it('draws the strategy and buy-and-hold lines, described for screen readers', () => {
    const svg: SVGElement = fixture.nativeElement.querySelector('svg');

    expect(svg.querySelectorAll('path.line').length).toBe(2);
    expect(svg.getAttribute('role')).toBe('img');
    expect(svg.getAttribute('aria-label')).toBe(
      'Backtest from 2024-01-02 to 2024-12-31: the strategy ends at $12.5K, buying and holding at $11K.'
    );
  });

  it('reads out both values under the pointer', () => {
    const svg: SVGSVGElement = fixture.nativeElement.querySelector('svg');
    const left = svg.getBoundingClientRect().left;

    svg.dispatchEvent(new PointerEvent('pointermove', { clientX: left + 390 }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.readout').textContent).toContain('$12,500 vs $11,000');
    svg.dispatchEvent(new PointerEvent('pointerleave'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.readout')).toBeNull();
  });

  it('describes an empty backtest', () => {
    fixture.componentRef.setInput('curve', []);
    fixture.detectChanges();

    expect(fixture.componentInstance.summary()).toBe('No backtest values');
    expect(fixture.nativeElement.querySelector('svg')).toBeNull();
  });
});
