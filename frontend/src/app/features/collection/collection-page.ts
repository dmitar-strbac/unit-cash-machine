import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import {
  EMPTY,
  Observable,
  catchError,
  exhaustMap,
  filter,
  finalize,
  of,
  switchMap,
  tap,
  throwError,
  timer,
} from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import {
  CollectionStep,
  CollectionView,
  PhysicalAction,
} from '../../core/collection/collection.models';
import { CollectionService } from '../../core/collection/collection.service';

type Command =
  | { id: string; kind: 'removal' }
  | { id: string; kind: 'insertion' }
  | { id: string; kind: 'simulate'; action: PhysicalAction };

@Component({
  selector: 'app-collection-page',
  templateUrl: './collection-page.html',
  styleUrl: './collection-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CollectionPage {
  private readonly api = inject(CollectionService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private revision = 0;
  private completionAttempted = false;

  protected readonly collection = signal<CollectionView | null>(null);
  protected readonly busy = signal(false);
  protected readonly restarting = signal(false);
  protected readonly closing = signal(false);
  protected readonly sessionClosed = signal(false);
  protected readonly loadError = signal('');
  protected readonly actionError = signal('');
  protected readonly closeError = signal('');
  protected readonly pending = signal<Command | null>(null);

  protected readonly titles: Record<CollectionStep, string> = {
    OPEN_DOOR: 'Open the door',
    REMOVE_CASSETTE: 'Remove cassette',
    INSERT_CASSETTE: 'Insert NEW cassette',
    CLOSE_DOOR: 'Close the door',
    SECURING: 'Securing gate',
    INCOMPLETE: 'CIT not completed',
    COMPLETED: 'CIT completed',
  };

  protected readonly controlsEnabled = computed(
    () =>
      this.collection() !== null &&
      !this.busy() &&
      !this.restarting() &&
      !this.pending() &&
      !this.loadError() &&
      this.collection()?.step !== 'COMPLETED',
  );

  protected readonly nextVisible = computed(() =>
    ['REMOVE_CASSETTE', 'INSERT_CASSETTE'].includes(this.collection()?.step ?? ''),
  );

  protected readonly cassetteControls = this.nextVisible;

  protected readonly gateControls = computed(() =>
    ['CLOSE_DOOR', 'SECURING', 'INCOMPLETE'].includes(this.collection()?.step ?? ''),
  );

  protected readonly recoveryMessage = computed(() => {
    const code = this.collection()?.errorCode;

    if (code === 'LOCK_TIMEOUT') {
      return 'Turn the lock to complete the procedure. Use RESTART if you need to sign in again.';
    }

    if (code === 'GATE_HARDWARE_UNAVAILABLE' || code === 'DOOR_STATE_UNKNOWN') {
      return 'Check the gate hardware and diagnostics before restarting.';
    }

    return 'The procedure remains unfinished. Check the door and lock, then restart with your CIT PIN.';
  });

  constructor() {
    timer(0, 1000)
      .pipe(
        filter(() => !this.busy() && !this.restarting() && this.collection()?.step !== 'COMPLETED'),
        exhaustMap(() => {
          const revision = this.revision;

          return this.api.current().pipe(
            tap((view) => {
              if (revision === this.revision && !this.busy() && !this.restarting()) {
                this.apply(view);
                this.finishSessionIfCompleted();
              }
            }),
            catchError((error: unknown) => {
              if (revision === this.revision && !this.restarting()) {
                this.loadError.set('Unable to refresh the procedure. Check the connection.');
                this.handleExpiredSession(error);
              }

              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  protected next(): void {
    const view = this.collection();

    if (!view || !this.controlsEnabled() || !this.nextVisible()) {
      return;
    }

    this.send({
      id: view.id,
      kind: view.step === 'REMOVE_CASSETTE' ? 'removal' : 'insertion',
    });
  }

  protected simulate(action: PhysicalAction): void {
    const view = this.collection();

    if (view && this.controlsEnabled()) {
      this.send({ id: view.id, kind: 'simulate', action });
    }
  }

  protected retry(): void {
    const command = this.pending();

    if (command && !this.busy() && !this.restarting()) {
      this.send(command);
    }
  }

  protected restart(): void {
    const view = this.collection();

    if (!view || view.step !== 'INCOMPLETE' || !this.controlsEnabled()) {
      return;
    }

    this.revision++;
    this.restarting.set(true);
    this.actionError.set('');

    this.api
      .restart(view.id)
      .pipe(
        catchError((error: unknown) =>
          this.auth
            .refresh()
            .pipe(switchMap((user) => (user ? throwError(() => error) : of(undefined)))),
        ),
        finalize(() => this.restarting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.auth.clearSession('Enter your CIT PIN to continue the unfinished procedure.');

          void this.router.navigateByUrl('/login/cit', {
            replaceUrl: true,
          });
        },
        error: (error: unknown) => {
          this.actionError.set(this.errorMessage(error));
          this.handleExpiredSession(error);
        },
      });
  }

  protected retrySessionClose(): void {
    this.completionAttempted = false;
    this.finishSessionIfCompleted();
  }

  private send(command: Command): void {
    this.revision++;
    this.busy.set(true);
    this.pending.set(command);
    this.actionError.set('');

    let request: Observable<unknown>;

    if (command.kind === 'removal') {
      request = this.api.confirmRemoval(command.id);
    } else if (command.kind === 'insertion') {
      request = this.api.confirmInsertion(command.id);
    } else {
      request = this.api.simulate(command.id, command.action);
    }

    request
      .pipe(
        switchMap(() => this.api.current()),
        finalize(() => {
          this.busy.set(false);
          this.finishSessionIfCompleted();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (view) => {
          this.pending.set(null);
          this.actionError.set('');
          this.apply(view);
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && [400, 403, 404, 409].includes(error.status)) {
            this.pending.set(null);
            this.loadError.set('Refresh the procedure before trying another action.');
            this.actionError.set(this.errorMessage(error));
          } else {
            this.actionError.set(
              'The result is unconfirmed. Check the last action before continuing.',
            );
          }

          this.handleExpiredSession(error);
        },
      });
  }

  private apply(view: CollectionView): void {
    this.collection.set(view);
    this.loadError.set('');

    const pending = this.pending();

    if (!pending) {
      return;
    }

    const confirmed =
      view.step === 'COMPLETED' ||
      (pending.kind === 'removal' && view.removalConfirmed) ||
      (pending.kind === 'insertion' && view.insertionConfirmed) ||
      (pending.kind === 'simulate' &&
        ((pending.action === 'OPEN_DOOR' &&
          ['REMOVE_CASSETTE', 'INSERT_CASSETTE', 'CLOSE_DOOR'].includes(view.step)) ||
          (pending.action === 'CLOSE_DOOR' && view.doorClosed) ||
          (pending.action === 'LOCK_DOOR' && view.doorClosed && view.doorLocked)));

    if (confirmed) {
      this.pending.set(null);
      this.actionError.set('');
    }
  }

  private finishSessionIfCompleted(): void {
    if (this.collection()?.step !== 'COMPLETED' || this.busy() || this.completionAttempted) {
      return;
    }

    this.completionAttempted = true;
    this.closing.set(true);
    this.closeError.set('');

    this.auth
      .logout()
      .pipe(
        catchError((error: unknown) =>
          this.auth
            .refresh()
            .pipe(switchMap((user) => (user ? throwError(() => error) : of(undefined)))),
        ),
        finalize(() => this.closing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.auth.clearSession();
          this.sessionClosed.set(true);

          timer(4000)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
              void this.router.navigateByUrl('/', {
                replaceUrl: true,
              });
            });
        },
        error: () => {
          this.closeError.set(
            'The collection is complete, but the session could not be closed. Check the connection and retry.',
          );
        },
      });
  }

  private handleExpiredSession(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      this.auth.clearSession('Your CIT session has ended. Enter your PIN to resume the procedure.');

      void this.router.navigateByUrl('/login/cit', {
        replaceUrl: true,
      });
    }
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 404) {
        return 'The requested simulation action is unavailable. Check the backend profile.';
      }

      if (error.status === 403) {
        return 'Access denied or security check failed. Refresh and try again.';
      }

      const problem = error.error as { detail?: unknown } | null;

      if (typeof problem?.detail === 'string') {
        return problem.detail;
      }
    }

    return 'The action could not be completed. Check the procedure and diagnostics.';
  }
}
