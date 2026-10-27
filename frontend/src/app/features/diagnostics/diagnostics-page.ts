import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MachineStatusService } from '../../core/machine/machine-status.service';

@Component({
  selector: 'app-diagnostics-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-labelledby="diagnostics-title">
      <h1 id="diagnostics-title">Machine diagnostics</h1>

      @if (connection(); as current) {
        @if (current.state === 'connected') {
          @if (current.status.alertCount === 0) {
            <p class="message">No active hardware warnings or errors.</p>
          }

          <div class="alerts">
            @for (alert of current.status.alerts; track alert.code) {
              <article class="alert" [class.error]="alert.severity === 'ERROR'">
                <div class="alert-heading">
                  <strong>{{ alert.source.replaceAll('_', ' ') }}</strong>
                  <span>{{ alert.severity }}</span>
                </div>

                <p>{{ alert.message }}</p>

                <small>
                  {{ alert.blocksDeposit ? 'Deposits blocked' : 'Does not block deposits' }}
                </small>
              </article>
            }
          </div>
        } @else {
          <p class="message" role="status">
            Connection to the machine is unavailable. Retrying automatically…
          </p>
        }
      } @else {
        <p class="message" role="status">Reading machine status…</p>
      }
    </section>
  `,
  styles: `
    .alerts {
      display: grid;
      gap: 14px;
    }

    .alert {
      padding: 18px;
      border: 1px solid var(--border);
      border-left: 6px solid var(--warning);
      background: white;
    }

    .alert.error {
      border-left-color: var(--danger);
    }

    .alert-heading {
      display: flex;
      justify-content: space-between;
      gap: 16px;
    }

    .alert-heading span,
    small {
      font-size: 14px;
      color: var(--muted);
    }

    p {
      margin: 12px 0;
    }
  `,
})
export class DiagnosticsPage {
  protected readonly connection = toSignal(inject(MachineStatusService).connection$);
}
