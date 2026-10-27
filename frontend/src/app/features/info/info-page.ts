import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MachineStatusService } from '../../core/machine/machine-status.service';

@Component({
  selector: 'app-info-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-labelledby="info-title">
      <h1 id="info-title">Machine information</h1>

      @if (connection(); as current) {
        @if (current.state === 'connected') {
          <dl>
            <dt>Machine code</dt>
            <dd>{{ current.status.machineCode }}</dd>

            <dt>Name</dt>
            <dd>{{ current.status.displayName }}</dd>

            <dt>Location</dt>
            <dd>{{ current.status.location }}</dd>

            <dt>Currency</dt>
            <dd>{{ current.status.currency }}</dd>

            <dt>Time zone</dt>
            <dd>{{ current.status.timeZone }}</dd>

            <dt>Mode</dt>
            <dd>{{ current.status.simulated ? 'Simulation' : 'Hardware' }}</dd>
          </dl>
        } @else {
          <p class="message">Machine information is unavailable. Retrying automatically…</p>
        }
      } @else {
        <p class="message">Loading machine information…</p>
      }
    </section>
  `,
  styles: `
    dl {
      display: grid;
      grid-template-columns: minmax(100px, 1fr) 2fr;
      gap: 18px;
      font-size: clamp(16px, 2vw, 22px);
    }

    dt {
      color: var(--muted);
    }

    dd {
      margin: 0;
      overflow-wrap: anywhere;
    }
  `,
})
export class InfoPage {
  protected readonly connection = toSignal(inject(MachineStatusService).connection$);
}
