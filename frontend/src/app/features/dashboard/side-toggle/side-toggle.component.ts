import { Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { OrderSide } from '../trading.models';

/** BUY/SELL switch for the order form; the thumb slides under the selected side. */
@Component({
  selector: 'app-side-toggle',
  templateUrl: './side-toggle.component.html',
  styleUrl: './side-toggle.component.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SideToggleComponent), multi: true },
  ],
})
export class SideToggleComponent implements ControlValueAccessor {
  readonly sides: OrderSide[] = ['BUY', 'SELL'];

  /** Id of the element that labels the group. */
  readonly labelledBy = input<string | null>(null);

  readonly value = signal<OrderSide | null>(null);
  readonly disabled = signal(false);

  private onChange: (value: OrderSide) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  select(side: OrderSide): void {
    if (this.disabled() || side === this.value()) {
      return;
    }
    this.value.set(side);
    this.onChange(side);
  }

  markTouched(): void {
    this.onTouched();
  }

  writeValue(value: OrderSide | null): void {
    this.value.set(value);
  }

  registerOnChange(fn: (value: OrderSide) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }
}
