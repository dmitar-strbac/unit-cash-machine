import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { EMPTY, catchError, exhaustMap, filter, map, timer } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
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

  protected readonly auth = inject(AuthService);
  protected readonly connection = toSignal(inject(MachineStatusService).connection$);

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly backTarget = computed(() => {
    const url = this.currentUrl();
    const path = url.split(/[?#]/)[0];

    if (path === '/' || path === '/session') {
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
      return this.auth.user() ? '/session' : '/';
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

  constructor() {
    timer(0, 15000)
      .pipe(
        filter((tick) => tick === 0 || this.auth.user() !== null),
        exhaustMap(() => this.auth.refresh().pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        if (!this.auth.user() && this.router.url.split(/[?#]/)[0] === '/session') {
          void this.router.navigateByUrl('/', { replaceUrl: true });
        }
      });
  }
}
