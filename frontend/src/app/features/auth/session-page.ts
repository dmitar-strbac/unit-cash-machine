import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { SessionCoordinator } from '../../core/auth/session-coordinator.service';
import { DepositService } from '../../core/deposit/deposit.service';
import { DepositPanel } from '../deposit/deposit-panel';

@Component({
  selector: 'app-session-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DepositPanel, RouterLink],
  template: `
    <section class="session-page" aria-label="Current session">
      @if (auth.user(); as user) {
        @switch (user.role) {
          @case ('CASHIER') {
            <app-deposit-panel [ending]="busy()" />
          }
          @case ('MANAGEMENT') {
            <div class="session-menu">
              <a class="machine-button menu-button" routerLink="/inventory">INVENTORY</a>
              <button class="machine-button menu-button" disabled>SYSTEM</button>
            </div>
          }

          @case ('MAINTENANCE') {
            <div class="session-menu">
              <button class="machine-button menu-button" disabled>SYSTEM</button>
              <button class="machine-button menu-button" disabled>COMPONENT INFO</button>
            </div>
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
  protected readonly deposits = inject(DepositService);
  private readonly session = inject(SessionCoordinator);

  protected readonly busy = this.session.ending;
  protected readonly error = this.session.error;

  protected logout(): void {
    this.session.logout();
  }
}
