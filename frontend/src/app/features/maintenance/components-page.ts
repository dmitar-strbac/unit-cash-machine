import { DatePipe, KeyValuePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, map } from 'rxjs';
import { MachineComponent } from '../../core/maintenance/maintenance.models';
import { MaintenanceService, maintenanceError } from '../../core/maintenance/maintenance.service';
import { MaintenanceIcon } from './maintenance-icon';

@Component({
  selector: 'app-components-page',
  imports: [DatePipe, KeyValuePipe, MaintenanceIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-label="Component information">
      @if (loading()) {
        <p class="message" role="status">Reading components…</p>
      } @else if (error()) {
        <div class="message" role="alert">
          <p>{{ error() }}</p>
          <button class="machine-button retry" (click)="load()">RETRY</button>
        </div>
      } @else if (selected(); as item) {
        <dl class="details">
          <dt>ID</dt>
          <dd>{{ item.id }}</dd>

          <dt>Description</dt>
          <dd>{{ item.description }}</dd>

          <dt>State</dt>
          <dd>{{ item.state }}</dd>

          @for (entry of item.metadata | keyvalue; track entry.key) {
            <dt>{{ metadataLabel(entry.key) }}</dt>
            <dd>{{ entry.value }}</dd>
          }

          @if (!item.derived) {
            <dt>Driver resets</dt>
            <dd>{{ item.resetCount }}</dd>

            <dt>Last reset</dt>
            <dd>
              {{ item.lastResetAt ? (item.lastResetAt | date: 'medium') : 'Never' }}
            </dd>
          }

          <dt>Mode</dt>
          <dd>{{ item.derived ? 'Derived status' : 'Simulated component' }}</dd>
        </dl>
      } @else {
        <div class="component-grid">
          @for (item of items(); track item.id) {
            <button class="machine-button component-button" (click)="open(item)">
              <app-maintenance-icon [name]="icon(item.id)" />
              <span>
                {{ item.description }}
                <small>{{ item.state }}</small>
              </span>
            </button>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .component-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 14px;
    }

    .component-button {
      min-height: 108px;
      padding: 16px;
      justify-content: flex-start;
      gap: 14px;
      text-align: left;
      font-size: 18px;
    }

    small {
      display: block;
      color: var(--muted);
      font-size: 13px;
      margin-top: 8px;
    }

    .pagination {
      display: flex;
      justify-content: flex-end;
      align-items: center;
      gap: 20px;
      margin-top: 16px;
    }

    .pagination button {
      width: 60px;
      height: 48px;
      font-size: 32px;
    }

    .details {
      display: grid;
      grid-template-columns: minmax(130px, 1fr) minmax(0, 2fr);
      gap: 14px 24px;
      font-size: 20px;
      margin: 12px 0;
    }

    dt {
      font-weight: 600;
    }

    dd {
      margin: 0;
      overflow-wrap: anywhere;
    }

    .retry {
      padding: 12px 32px;
    }

    @media (max-width: 700px) {
      .component-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .details {
        font-size: 16px;
      }
    }
  `,
})
export class ComponentsPage {
  private readonly maintenance = inject(MaintenanceService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly items = signal<MachineComponent[]>([]);

  private readonly selectedId = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('id'))),
  );

  protected readonly loading = signal(false);
  protected readonly error = signal('');

  protected readonly selected = computed(() =>
    this.items().find((item) => item.id === this.selectedId()),
  );

  constructor() {
    this.load();
  }

  protected load(): void {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.error.set('');

    this.maintenance
      .components()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (items) => this.items.set(items),
        error: (error: unknown) => this.error.set(maintenanceError(error)),
      });
  }

  protected open(item: MachineComponent): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { id: item.id },
    });
  }

  protected metadataLabel(key: string): string {
    const labels: Record<string, string> = {
      model: 'Model',
      serialNumber: 'Serial number',
      driverVersion: 'Driver version',
    };

    return labels[key] ?? key;
  }

  protected icon(id: string): string {
    const icons: Record<string, string> = {
      ACCEPTOR: 'acceptor',
      CASHBOX: 'cassette',
      DOOR: 'door',
      LOCK: 'lock',
      EXTENSION_BOARD: 'board',
      PRINTER: 'printer',
      DOORS_CLOSED: 'doorClosed',
      LOCKS_SECURE: 'lockSecure',
      MACHINE_SECURE: 'shield',
    };

    return icons[id] ?? 'components';
  }
}
