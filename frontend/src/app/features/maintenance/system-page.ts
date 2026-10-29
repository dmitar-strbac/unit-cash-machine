import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { MaintenanceAction } from '../../core/maintenance/maintenance.models';
import { MaintenanceService, maintenanceError } from '../../core/maintenance/maintenance.service';
import { MaintenanceIcon } from './maintenance-icon';

@Component({
  selector: 'app-system-page',
  imports: [MaintenanceIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-label="System maintenance">
      @if (selected(); as action) {
        <section class="message confirmation" aria-labelledby="confirm-title">
          <h1 id="confirm-title">{{ label(action) }}?</h1>
          <p>{{ description(action) }}</p>

          @if (error()) {
            <p role="alert">{{ error() }}</p>
          }

          <div class="confirmation-actions">
            <button class="machine-button" [disabled]="busy() || uncertain()" (click)="cancel()">
              CANCEL
            </button>

            <button
              class="machine-button"
              [disabled]="busy() || !maintenance.operational()"
              (click)="confirm()"
            >
              {{ busy() ? 'PLEASE WAIT…' : uncertain() ? 'RETRY SAME COMMAND' : 'CONFIRM' }}
            </button>
          </div>
        </section>
      } @else {
        <div class="system-grid">
          @if (auth.user()?.role === 'MAINTENANCE') {
            <button
              class="machine-button system-button"
              [disabled]="!maintenance.operational()"
              (click)="select('SHUT_DOWN')"
            >
              <app-maintenance-icon name="power" />
              <span>SHUT DOWN</span>
            </button>
          }

          <button
            class="machine-button system-button"
            [disabled]="!maintenance.operational()"
            (click)="select('REBOOT')"
          >
            <app-maintenance-icon name="reboot" />
            <span>REBOOT</span>
          </button>

          <button
            class="machine-button system-button"
            [disabled]="!maintenance.operational()"
            (click)="select('RESET')"
          >
            <app-maintenance-icon name="reset" />
            <span>RESET</span>
          </button>

          @if (auth.user()?.role === 'MAINTENANCE') {
            <button
              class="machine-button system-button"
              [disabled]="!maintenance.operational()"
              (click)="select('EXIT_APPLICATION')"
            >
              <app-maintenance-icon name="exit" />
              <span>EXIT APPLICATION</span>
            </button>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .system-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }

    .system-button {
      min-height: 120px;
      justify-content: flex-start;
      gap: 28px;
      padding: 24px;
      font-size: clamp(18px, 2.4vw, 26px);
    }

    .confirmation {
      max-width: 760px;
      margin: 24px auto;
    }

    .confirmation-actions {
      display: flex;
      gap: 16px;
    }

    .confirmation-actions button {
      flex: 1;
      min-height: 64px;
      padding: 12px;
    }

    @media (max-width: 600px) {
      .system-button {
        gap: 12px;
        padding: 16px;
      }
    }
  `,
})
export class SystemPage {
  protected readonly auth = inject(AuthService);
  protected readonly maintenance = inject(MaintenanceService);

  private readonly destroyRef = inject(DestroyRef);

  protected readonly selected = signal<MaintenanceAction | null>(null);
  protected readonly busy = signal(false);
  protected readonly uncertain = signal(false);
  protected readonly error = signal('');

  private requestId = '';

  protected select(action: MaintenanceAction): void {
    this.requestId = crypto.randomUUID();
    this.selected.set(action);
    this.error.set('');
    this.uncertain.set(false);
  }

  protected cancel(): void {
    this.selected.set(null);
  }

  protected confirm(): void {
    const action = this.selected();

    if (!action || this.busy()) {
      return;
    }

    this.busy.set(true);
    this.error.set('');

    this.maintenance
      .execute(action, this.requestId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.selected.set(null);
          this.uncertain.set(false);
        },
        error: (error: unknown) => {
          this.error.set(maintenanceError(error));

          this.uncertain.set(
            !(
              error instanceof HttpErrorResponse && [400, 401, 403, 409, 503].includes(error.status)
            ),
          );
        },
      });
  }

  protected label(action: MaintenanceAction): string {
    return action.replaceAll('_', ' ');
  }

  protected description(action: MaintenanceAction): string {
    switch (action) {
      case 'RESET':
        return 'Reset all component drivers. You remain signed in. Physical hardware faults may still require intervention.';
      case 'REBOOT':
        return 'Restart the simulated machine. All operators will be signed out. Recorded money and reports are preserved.';
      case 'SHUT_DOWN':
        return 'Shut down the simulated machine. Use the simulator power button to turn it on again.';
      default:
        return 'Close the machine application and return to the simulated desktop.';
    }
  }
}
