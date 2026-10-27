import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { MachineStatusService } from './core/machine/machine-status.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly router = inject(Router);

  protected readonly connection = toSignal(inject(MachineStatusService).connection$);

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly isHome = computed(() => this.currentUrl().split(/[?#]/)[0] === '/');

  protected readonly indicator = computed(() => {
    const current = this.connection();

    return current?.state === 'connected' ? current.status.indicator.toLowerCase() : 'unknown';
  });

  protected readonly statusLabel = computed(() => {
    const current = this.connection();

    if (!current) {
      return 'Connecting…';
    }

    if (current.state === 'disconnected') {
      return 'Connection lost';
    }

    const count = current.status.alertCount;

    return count === 0 ? 'No alerts' : `${count} ${count === 1 ? 'alert' : 'alerts'}`;
  });
}
