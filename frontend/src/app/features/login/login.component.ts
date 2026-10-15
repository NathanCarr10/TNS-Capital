import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly loginForm = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  onSubmit(): void {
    if (this.loginForm.invalid || this.isSubmitting()) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.isSubmitting.set(true);

    this.authService.login(this.loginForm.getRawValue()).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        const returnUrl = this.safeReturnUrl();
        if (returnUrl) {
          void this.router.navigateByUrl(returnUrl);
        } else {
          void this.router.navigate(['/portfolio']);
        }
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(AuthService.getErrorMessage(error));
      },
    });
  }

  // Only a path in this app (the router never leaves it); anything else gets the normal landing page
  private safeReturnUrl(): string | null {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    return returnUrl?.startsWith('/') && !returnUrl.startsWith('//') && !returnUrl.startsWith('/login')
      ? returnUrl
      : null;
  }
}
