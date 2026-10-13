/// <reference types="jasmine" />

import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { OrderSide } from '../trading.models';
import { SideToggleComponent } from './side-toggle.component';

@Component({
  imports: [ReactiveFormsModule, SideToggleComponent],
  template: '<app-side-toggle [formControl]="side" />',
})
class HostComponent {
  readonly side = new FormControl<OrderSide>('BUY', { nonNullable: true });
}

describe('SideToggleComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  const buttons = (): HTMLButtonElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll('button'));

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows the control value as the checked side', () => {
    expect(buttons().map((button) => button.getAttribute('aria-checked'))).toEqual(['true', 'false']);

    host.side.setValue('SELL');
    fixture.detectChanges();

    expect(buttons().map((button) => button.getAttribute('aria-checked'))).toEqual(['false', 'true']);
    expect(fixture.nativeElement.querySelector('.side-toggle').classList).toContain('sell');
  });

  it('updates the control when a side is clicked', () => {
    buttons()[1].click();

    expect(host.side.value).toBe('SELL');
  });

  it('ignores clicks while the control is disabled', () => {
    host.side.disable();
    fixture.detectChanges();

    buttons()[1].click();

    expect(host.side.value).toBe('BUY');
  });
});
