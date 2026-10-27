import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { catchError, exhaustMap, map, of, shareReplay, timeout, timer } from 'rxjs';
import { MachineStatus } from './machine-status';

export type MachineConnection =
  { state: 'connected'; status: MachineStatus } | { state: 'disconnected'; status: null };

@Injectable({ providedIn: 'root' })
export class MachineStatusService {
  private readonly http = inject(HttpClient);

  readonly connection$ = timer(0, 3000).pipe(
    exhaustMap(() =>
      this.http.get<MachineStatus>('/api/machine/status').pipe(
        timeout(2500),
        map((status): MachineConnection => ({
          state: 'connected',
          status,
        })),
        catchError(() =>
          of<MachineConnection>({
            state: 'disconnected',
            status: null,
          }),
        ),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
}
