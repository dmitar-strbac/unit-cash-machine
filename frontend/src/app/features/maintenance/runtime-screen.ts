import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { MaintenanceAction } from '../../core/maintenance/maintenance.models';
import { MaintenanceService, maintenanceError } from '../../core/maintenance/maintenance.service';
import { MaintenanceIcon } from './maintenance-icon';

@Component({
  selector: 'app-runtime-screen',
  imports: [MaintenanceIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section
      class="runtime-screen"
      [class.desktop]="maintenance.runtime()?.state === 'APPLICATION_STOPPED'"
      [class.off]="maintenance.runtime()?.state === 'SHUT_DOWN'"
      aria-live="polite"
    >
      @switch (maintenance.runtime()?.state) {
        @case ('SHUT_DOWN') {
          <app-maintenance-icon name="power" />
          <h1>Machine powered off</h1>
        }

        @case ('APPLICATION_STOPPED') {
          <app-maintenance-icon name="system" />
          <h1>Simulator desktop</h1>
          <p>The machine application is closed.</p>
        }

        @default {
          <app-maintenance-icon name="reboot" />
          <h1>Starting machine…</h1>
          <p>Please wait. The sign-in screen will return automatically.</p>
        }
      }

      @if (maintenance.disconnected()) {
        <p role="status">Connection unavailable. Retrying automatically…</p>
      }

      @if (error()) {
        <p role="alert">{{ error() }}</p>
      }
    </section>

    @if (
      maintenance.runtime()?.state === 'SHUT_DOWN' ||
      maintenance.runtime()?.state === 'APPLICATION_STOPPED'
    ) {
      <div class="simulator-controls">
        <button
          class="machine-button"
          [disabled]="busy() || maintenance.disconnected() || maintenance.awaiting()"
          (click)="start()"
        >
          {{
            busy()
              ? 'PLEASE WAIT…'
              : maintenance.runtime()?.state === 'SHUT_DOWN'
                ? 'POWER ON SIMULATOR'
                : 'START MACHINE APPLICATION'
          }}
        </button>

        <small>Simulator controls</small>
      </div>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      grid-column: 1;
      grid-row: 1 / -1;
    }

    .runtime-screen {
      display: flex;
      flex: 1;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 28px;
      text-align: center;
      background: #26343e;
      color: white;
    }

    h1 {
      margin: 22px 0 12px;
    }

    .off {
      background: #10171d;
    }

    .desktop {
      background: linear-gradient(135deg, #31546c, #182d40);
    }

    .simulator-controls {
      display: grid;
      justify-items: center;
      gap: 8px;
      background: #202c35;
      padding: 18px;
      color: #aebec8;
    }

    button {
      min-height: 56px;
      padding: 12px 28px;
    }
  `,
})
export class RuntimeScreen {
  protected readonly maintenance = inject(MaintenanceService);

  private readonly destroyRef = inject(DestroyRef);

  protected readonly busy = signal(false);
  protected readonly error = signal('');

  private pending: { action: MaintenanceAction; id: string } | null = null;

  protected start(): void {
    if (this.busy()) {
      return;
    }

    const action =
      this.maintenance.runtime()?.state === 'SHUT_DOWN' ? 'POWER_ON' : 'START_APPLICATION';

    if (this.pending?.action !== action) {
      this.pending = { action, id: crypto.randomUUID() };
    }

    this.busy.set(true);
    this.error.set('');

    this.maintenance
      .execute(action, this.pending.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.pending = null;
        },
        error: (error: unknown) => this.error.set(maintenanceError(error)),
      });
  }
}
