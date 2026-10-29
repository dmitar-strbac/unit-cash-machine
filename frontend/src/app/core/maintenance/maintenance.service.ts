import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { EMPTY, Subject, catchError, exhaustMap, merge, tap, timeout, timer } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { CsrfHttpService } from '../http/csrf-http.service';
import {
  MachineComponent,
  MachineRuntimeView,
  MaintenanceAction,
  MaintenanceCommand,
} from './maintenance.models';

@Injectable({ providedIn: 'root' })
export class MaintenanceService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfHttpService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly reload = new Subject<void>();

  private revision = 0;

  readonly runtime = signal<MachineRuntimeView | null>(null);
  readonly disconnected = signal(false);
  readonly awaiting = signal(false);

  readonly blocked = computed(() => {
    const state = this.runtime()?.state;

    return !!state && state !== 'RUNNING' && state !== 'RESETTING';
  });

  readonly operational = computed(
    () => this.runtime()?.state === 'RUNNING' && !this.disconnected() && !this.awaiting(),
  );

  constructor() {
    merge(timer(0, 1000), this.reload)
      .pipe(
        exhaustMap(() => {
          const revision = this.revision;

          return this.http.get<MachineRuntimeView>('/api/machine/runtime').pipe(
            timeout(2500),
            tap((view) => {
              if (revision === this.revision) {
                this.disconnected.set(false);
                this.awaiting.set(false);
                this.apply(view);
              }
            }),
            catchError(() => {
              if (revision === this.revision) {
                this.disconnected.set(true);
              }

              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  execute(action: MaintenanceAction, requestId: string) {
    const endpoint =
      action === 'POWER_ON'
        ? '/api/simulator/power-on'
        : action === 'START_APPLICATION'
          ? '/api/simulator/start-application'
          : '/api/maintenance/commands';

    return this.csrf.post<MaintenanceCommand>(endpoint, { requestId, action }).pipe(
      tap(() => {
        this.revision++;
        this.awaiting.set(true);
        this.reload.next();
      }),
    );
  }

  components() {
    return this.http.get<MachineComponent[]>('/api/maintenance/components').pipe(timeout(8000));
  }

  private apply(view: MachineRuntimeView): void {
    const previous = this.runtime();

    if (previous && view.generation < previous.generation) {
      return;
    }

    this.runtime.set(view);

    if (this.blocked() || (previous && view.generation !== previous.generation)) {
      if (this.auth.user()) {
        this.auth.clearSession();
      }

      if (this.router.url !== '/') {
        void this.router.navigateByUrl('/', { replaceUrl: true });
      }
    }
  }
}

export function maintenanceError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 401) {
      return 'Your session has ended. Sign in again.';
    }

    if (error.status === 403) {
      return 'This action is not permitted for your account.';
    }

    if (error.status === 409) {
      return error.error?.detail ?? 'Finish the active deposit or CIT procedure first.';
    }

    if (error.status === 503) {
      return 'The machine is busy. Wait for the current operation to finish.';
    }
  }

  return 'Unable to confirm the result. Retry to check the same command.';
}
