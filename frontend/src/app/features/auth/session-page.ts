import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { authErrorMessage } from '../../core/auth/auth-error';
import { AuthService } from '../../core/auth/auth.service';
import { DepositService } from '../../core/deposit/deposit.service';
import { DepositPanel } from '../deposit/deposit-panel';

@Component({
  selector: 'app-session-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DepositPanel],
  template: `
    <section class="session-page" aria-label="Current session">
      @if (auth.user(); as user) {
        @switch (user.role) {
          @case ('CASHIER') {
            <app-deposit-panel [ending]="busy()" />
          }
          @case ('MANAGEMENT') {
            <div class="session-menu">
              <button class="machine-button menu-button" disabled>INVENTORY</button>
              <button class="machine-button menu-button" disabled>SYSTEM</button>
            </div>
          }

          @case ('MAINTENANCE') {
            <div class="session-menu">
              <button class="machine-button menu-button" disabled>SYSTEM</button>
              <button class="machine-button menu-button" disabled>COMPONENT INFO</button>
            </div>
          }

          @case ('CIT') {
            <p class="cit-message">CIT access authorized.</p>
          }
        }

        <div class="logout-area">
          @if (error()) {
            <p class="logout-error" role="alert">{{ error() }}</p>
          }

          <button
            class="machine-button logout-button"
            [disabled]="
              busy() || (user.role === 'CASHIER' && (deposits.busy() || !!deposits.pending()))
            "
            (click)="logout()"
          >
            {{ busy() ? 'SIGNING OUT…' : 'LOG OUT' }}
          </button>
        </div>
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }

    .session-page {
      display: flex;
      flex-direction: column;
      min-height: 100%;
      gap: 24px;
    }

    .session-menu {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }

    .menu-button {
      min-height: 110px;
      padding: 16px;
      font-size: clamp(18px, 2.5vw, 28px);
    }

    .cit-message {
      margin: 24px 0;
      text-align: center;
      font-size: 26px;
    }

    .logout-area {
      margin-top: auto;
    }

    .logout-error {
      color: #a51e37;
      text-align: center;
    }

    .machine-button.logout-button {
      width: min(100%, 600px);
      min-height: 82px;
      margin: 0 auto;
      padding: 16px;
      border-color: var(--danger);
      background: var(--danger);
      color: white;
      font-size: 30px;
      font-weight: 700;
    }

    .machine-button.logout-button:hover:not(:disabled) {
      background: #bd2041;
    }

    .logout-button:disabled {
      opacity: 0.6;
    }
  `,
})
export class SessionPage {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly deposits = inject(DepositService);

  protected readonly busy = signal(false);
  protected readonly error = signal('');

  protected logout(): void {
    if (
      this.busy() ||
      (this.auth.user()?.role === 'CASHIER' &&
        (this.deposits.busy() || this.deposits.pending() !== null))
    ) {
      return;
    }

    this.busy.set(true);
    this.error.set('');

    this.auth
      .logout()
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.deposits.clear();
          void this.router.navigateByUrl('/', { replaceUrl: true });
        },
        error: (error: unknown) => {
          this.error.set(authErrorMessage(error));
        },
      });
  }
}
