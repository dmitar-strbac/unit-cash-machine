import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { SessionCoordinator } from './core/auth/session-coordinator.service';
import { MachineStatusService } from './core/machine/machine-status.service';
import { MaintenanceService } from './core/maintenance/maintenance.service';
import { RuntimeScreen } from './features/maintenance/runtime-screen';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RuntimeScreen],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly router = inject(Router);

  protected readonly auth = inject(AuthService);
  protected readonly connection = toSignal(inject(MachineStatusService).connection$);
  protected readonly maintenance = inject(MaintenanceService);
  protected readonly session = inject(SessionCoordinator);

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly sessionTarget = computed(() =>
    this.auth.user()?.role === 'CIT' ? '/cit' : '/session',
  );

  protected readonly backTarget = computed(() => {
    const url = this.currentUrl();
    const path = url.split(/[?#]/)[0];

    if (path === '/' || path === '/session' || path === '/cit') {
      return null;
    }

    if (path.startsWith('/login/')) {
      const hasSelectedUser = this.router.parseUrl(url).queryParams['user'] !== undefined;

      if (hasSelectedUser) {
        return path;
      }

      return path === '/login/cashier' ? '/' : '/settings';
    }

    if (path === '/info' || path === '/diagnostics') {
      return this.auth.user() ? this.sessionTarget() : '/';
    }

    if (path === '/inventory/device' || path === '/inventory/day') {
      return '/inventory';
    }

    if (path === '/inventory' || path === '/receipts') {
      return '/session';
    }

    if (path === '/system') {
      return '/session';
    }

    if (path === '/components') {
      return this.router.parseUrl(url).queryParams['id'] ? '/components' : '/session';
    }

    if (path === '/deposit-receipt') {
      return '/';
    }

    return '/';
  });

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
