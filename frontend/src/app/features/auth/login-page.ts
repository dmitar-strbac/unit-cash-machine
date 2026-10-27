import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { authErrorMessage } from '../../core/auth/auth-error';
import { UserRole, UserSummary } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login-page',
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly role = this.route.snapshot.data['role'] as UserRole;
  protected readonly isCit = this.role === 'CIT';

  protected readonly users = signal<UserSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly loadError = signal('');
  protected readonly error = signal('');
  protected readonly busy = signal(false);
  protected readonly pin = signal('');

  protected readonly digits = ['1', '2', '3', '4', '5', '6', '7', '8', '9'];

  private readonly queryParams = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });

  protected readonly selectedUser = computed(
    () => this.users().find((user) => user.id === this.queryParams().get('user')) ?? null,
  );

  protected readonly showPin = computed(() => this.isCit || this.selectedUser() !== null);

  protected readonly canSubmit = computed(
    () => this.showPin() && /^[0-9]{4,12}$/.test(this.pin()) && !this.busy(),
  );

  protected readonly roleLabel: Record<UserRole, string> = {
    CASHIER: 'Select user',
    MANAGEMENT: 'Select manager',
    MAINTENANCE: 'Select maintainer',
    CIT: 'CIT',
  };

  constructor() {
    effect(() => {
      this.queryParams();
      this.pin.set('');
      this.error.set('');
    });

    if (!this.isCit) {
      this.loadUsers();
    }
  }

  protected loadUsers(): void {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.loadError.set('');

    this.auth
      .users(this.role)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (users) => this.users.set(users),
        error: () => this.loadError.set('Unable to load users. Please try again.'),
      });
  }

  protected selectUser(user: UserSummary): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { user: user.id },
    });
  }

  protected appendDigit(digit: string): void {
    if (!this.busy() && this.pin().length < 12) {
      this.pin.update((value) => value + digit);
      this.error.set('');
    }
  }

  protected clearPin(): void {
    if (!this.busy()) {
      this.pin.set('');
      this.error.set('');
    }
  }

  protected removeDigit(): void {
    if (!this.busy()) {
      this.pin.update((value) => value.slice(0, -1));
      this.error.set('');
    }
  }

  protected updatePin(event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = input.value.replace(/[^0-9]/g, '').slice(0, 12);

    input.value = value;
    this.pin.set(value);
    this.error.set('');
  }

  protected submit(event: Event): void {
    event.preventDefault();

    if (!this.canSubmit()) {
      return;
    }

    this.busy.set(true);
    this.error.set('');

    this.auth
      .login(this.role, this.selectedUser()?.id ?? null, this.pin())
      .pipe(
        finalize(() => {
          this.busy.set(false);
          this.pin.set('');
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          void this.router.navigateByUrl('/session', { replaceUrl: true });
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 409) {
            void this.router.navigateByUrl('/session', { replaceUrl: true });
            return;
          }

          this.error.set(authErrorMessage(error));
        },
      });
  }
}
