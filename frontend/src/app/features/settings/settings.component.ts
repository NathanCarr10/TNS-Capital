import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ToastService } from '../../core/notifications/toast.service';
import { AccountStore } from '../../core/trading/account.store';
import { readErrorMessage } from '../../core/trading/api-error';
import { HOLDER_NAME_PATTERN } from '../../core/validation/input-patterns';

/** The trader's account details, and where they change the name the account is held under. */
@Component({
  selector: 'app-settings',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent {
  private readonly store = inject(AccountStore);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly isAdmin = this.store.isAdmin;
  readonly account = this.store.account;
  readonly accountLoading = this.store.accountLoading;
  readonly accountError = this.store.accountError;
  readonly username = computed(() => this.store.profile()?.username ?? '');

  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  readonly nameForm = inject(FormBuilder).nonNullable.group({
    holderName: ['', [Validators.required, Validators.maxLength(255), Validators.pattern(HOLDER_NAME_PATTERN)]],
  });

  startEditing(): void {
    this.nameForm.reset({ holderName: this.account()?.holderName ?? '' });
    this.saveError.set(null);
    this.editing.set(true);
  }

  cancelEditing(): void {
    this.editing.set(false);
    this.saveError.set(null);
  }

  saveHolderName(): void {
    const holderName = this.nameForm.controls.holderName.value.trim();
    if (this.nameForm.invalid || !holderName || this.saving()) {
      this.nameForm.markAllAsTouched();
      return;
    }
    if (holderName === this.account()?.holderName) {
      this.editing.set(false);
      return;
    }

    this.saving.set(true);
    this.saveError.set(null);
    this.store
      .updateHolderName(holderName)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.editing.set(false);
          this.toast.success('Account holder name updated.');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          // e.g. characters outside the allow-list (VAL-422)
          this.saveError.set(readErrorMessage(error, 'Could not update the holder name. Please try again.'));
        },
      });
  }
}
