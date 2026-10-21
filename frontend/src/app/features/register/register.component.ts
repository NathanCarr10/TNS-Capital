import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { HOLDER_NAME_PATTERN } from '../../core/validation/input-patterns';

@Component({
  selector: 'app-register',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly registerForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required]],
    holderName: ['', [Validators.maxLength(255), Validators.pattern(HOLDER_NAME_PATTERN)]],
  });

  /** Missing or malformed email, or missing or too-short password; character errors get their own messages */
  hasCredentialsError(): boolean {
    const { email, password, confirmPassword } = this.registerForm.controls;
    return this.registerForm.touched && (email.invalid || password.invalid || confirmPassword.invalid);
  }

  validationMessage(): string {
    if (this.errorMessage()) {
      return this.errorMessage()!;
    }
    if (this.registerForm.controls.holderName.hasError('pattern') && this.registerForm.controls.holderName.touched) {
      return "Account holder name can only contain letters, numbers, spaces and . , ' -";
    }
    return this.hasCredentialsError() ? 'Enter a valid email address and passwords with at least 8 characters.' : '';
  }

  onSubmit(): void {
    if (this.registerForm.invalid || this.isSubmitting()) {
      this.registerForm.markAllAsTouched();
      return;
    }

    const { email, password, confirmPassword, holderName } = this.registerForm.getRawValue();
    if (password !== confirmPassword) {
      this.errorMessage.set('Passwords do not match.');
      return;
    }

    this.errorMessage.set(null);
    this.isSubmitting.set(true);

    this.authService.register({
      email: email.trim().toLowerCase(),
      password,
      ...(holderName.trim() ? { holderName: holderName.trim() } : {}),
    }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        void this.router.navigate(['/login']);
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(AuthService.getErrorMessage(error, 'Unable to create your account.'));
      },
    });
  }
}