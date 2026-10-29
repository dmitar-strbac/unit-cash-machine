import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import {
  EMPTY,
  catchError,
  exhaustMap,
  filter,
  finalize,
  fromEvent,
  merge,
  of,
  switchMap,
  throwError,
  timer,
} from 'rxjs';
import { DepositService } from '../deposit/deposit.service';
import { CsrfHttpService } from '../http/csrf-http.service';
import { MaintenanceService } from '../maintenance/maintenance.service';
import { CashierReceiptService } from '../report/cashier-receipt.service';
import { AuthenticatedUser } from './auth.models';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class SessionCoordinator {
  private readonly auth = inject(AuthService);
  private readonly deposits = inject(DepositService);
  private readonly receipts = inject(CashierReceiptService);
  private readonly csrf = inject(CsrfHttpService);
  private readonly maintenance = inject(MaintenanceService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  private readonly storageKey = 'unit.inactivity';
  private current: AuthenticatedUser | null = null;
  private identity = '';
  private deadline = 0;
  private startedAt = 0;
  private queued = false;
  private activityBusy = false;
  private accessBusy = false;
  private nextActivityAt = 0;
  private nextLogoutAt = 0;

  readonly ending = signal(false);
  readonly timedOut = signal(false);
  readonly error = signal('');

  constructor() {
    effect(() => {
      const user = this.auth.user();
      untracked(() => this.synchronize(user));
    });

    effect(() => {
      const activity = this.deposits.activityAt();

      untracked(() => {
        if (this.current?.role === 'CASHIER' && activity >= this.startedAt) {
          this.setDeadline(activity + 60000);
        }
      });
    });

    merge(fromEvent(this.document, 'pointerdown'), fromEvent(this.document, 'keydown'))
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (this.current && this.current.role !== 'CIT' && !this.timedOut()) {
          this.queued = true;
          this.flushActivity();
        }
      });

    timer(0, 250)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (this.maintenance.blocked() || !this.current || this.current.role === 'CIT') {
          return;
        }

        if (Date.now() >= this.deadline) {
          if (Date.now() >= this.nextLogoutAt) {
            this.logout(true);
          }
        } else {
          this.flushActivity();
        }
      });

    timer(0, 5000)
      .pipe(
        filter(
          (tick) =>
            !this.maintenance.blocked() &&
            (tick === 0 || (this.auth.user() !== null && !this.ending())),
        ),
        exhaustMap(() => this.auth.refresh().pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  logout(automatic = false): void {
    const user = this.auth.user();

    if (this.maintenance.blocked() || !user || user.role === 'CIT' || this.ending()) {
      return;
    }

    if (
      user.role === 'CASHIER' &&
      (this.deposits.busy() || (!automatic && this.deposits.pending() !== null))
    ) {
      return;
    }

    if (automatic) {
      this.timedOut.set(true);
    }

    this.ending.set(true);
    this.error.set('');

    this.auth
      .logout()
      .pipe(
        catchError((error: unknown) =>
          this.auth
            .refresh()
            .pipe(switchMap((active) => (active ? throwError(() => error) : of(undefined)))),
        ),
        finalize(() => this.ending.set(false)),
      )
      .subscribe({
        next: () => {
          this.auth.clearSession(
            automatic
              ? 'Your session ended due to inactivity. Accepted money remains recorded.'
              : '',
          );
        },
        error: () => {
          this.nextLogoutAt = Date.now() + 5000;
          this.error.set(
            'Unable to confirm sign-out. Check the connection and retry. Accepted money remains recorded.',
          );
        },
      });
  }

  private synchronize(user: AuthenticatedUser | null): void {
    const identity = user ? `${user.id}:${user.expiresAt}` : '';

    if (identity === this.identity) {
      return;
    }

    const previous = this.current;
    this.current = user;
    this.identity = identity;
    this.queued = false;
    this.nextActivityAt = 0;
    this.nextLogoutAt = 0;
    this.timedOut.set(false);

    if (!user) {
      this.removeDeadline();

      if (this.maintenance.blocked()) {
        this.receipts.clear();

        if (!this.deposits.pending()) {
          this.deposits.clear();
        }

        return;
      }

      if (previous && previous.role !== 'CIT') {
        const needsReceipt =
          previous.role === 'CASHIER' &&
          ((this.deposits.deposit()?.totalCents ?? 0) > 0 || this.deposits.pending() !== null);

        if (!this.deposits.pending()) {
          this.deposits.clear();
        }

        void this.router.navigateByUrl(
          needsReceipt && this.receipts.access() ? '/deposit-receipt' : '/',
          { replaceUrl: true },
        );
      }

      return;
    }

    this.startedAt = Date.now();

    if (user.role !== 'CASHIER') {
      this.receipts.clear();
    }

    const restored = this.restoreDeadline(identity);
    const lifetime = user.role === 'CASHIER' ? 60000 : 30000;

    this.setDeadline(restored ?? Date.now() + lifetime);

    if (user.role === 'CASHIER') {
      this.prepareReceipt(identity);
    }
  }

  private flushActivity(): void {
    const user = this.current;

    if (
      this.maintenance.blocked() ||
      !user ||
      user.role === 'CIT' ||
      !this.queued ||
      this.activityBusy ||
      this.ending() ||
      this.timedOut() ||
      Date.now() < this.nextActivityAt
    ) {
      return;
    }

    const identity = this.identity;
    const requestedAt = Date.now();

    this.queued = false;
    this.activityBusy = true;
    this.nextActivityAt = requestedAt + 1000;

    this.csrf
      .post<void>('/api/auth/activity', {})
      .pipe(finalize(() => (this.activityBusy = false)))
      .subscribe({
        next: () => {
          if (identity === this.identity && !this.timedOut()) {
            this.setDeadline(requestedAt + (user.role === 'CASHIER' ? 60000 : 30000));

            if (user.role === 'CASHIER' && !this.receipts.access()) {
              this.prepareReceipt(identity);
            }
          }
        },
        error: () => {
          if (identity === this.identity) {
            this.nextActivityAt = Date.now() + 3000;
          }
        },
      });
  }

  private prepareReceipt(identity: string): void {
    if (this.accessBusy) {
      return;
    }

    this.accessBusy = true;

    this.receipts
      .prepare(identity)
      .pipe(finalize(() => (this.accessBusy = false)))
      .subscribe({
        error: () => {
          if (identity === this.identity) {
            this.error.set(
              'Receipt access could not be prepared. Touch the screen to retry. Management can retrieve saved receipts.',
            );
          }
        },
      });
  }

  private setDeadline(deadline: number): void {
    if (!this.current) {
      return;
    }

    this.deadline = Math.min(deadline, Date.parse(this.current.expiresAt));

    try {
      sessionStorage.setItem(
        this.storageKey,
        JSON.stringify({ identity: this.identity, deadline: this.deadline }),
      );
    } catch {
      return;
    }
  }

  private restoreDeadline(identity: string): number | null {
    try {
      const stored = JSON.parse(sessionStorage.getItem(this.storageKey) ?? '{}') as {
        identity?: string;
        deadline?: unknown;
      };

      return stored.identity === identity &&
        typeof stored.deadline === 'number' &&
        Number.isFinite(stored.deadline)
        ? stored.deadline
        : null;
    } catch {
      return null;
    }
  }

  private removeDeadline(): void {
    try {
      sessionStorage.removeItem(this.storageKey);
    } catch {
      return;
    }
  }
}
