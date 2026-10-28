import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { EMPTY, Observable, catchError, defer, finalize, tap, throwError, timeout } from 'rxjs';
import { CsrfHttpService } from '../http/csrf-http.service';
import { BanknoteResult, DepositView, PendingBanknote } from './deposit.models';

@Injectable({ providedIn: 'root' })
export class DepositService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfHttpService);
  private readonly storageKey = 'unit.pending-banknote';

  private revision = 0;

  readonly deposit = signal<DepositView | null>(null);
  readonly busy = signal(false);
  readonly pending = signal<PendingBanknote | null>(null);
  readonly notice = signal('');
  readonly loadError = signal('');

  open(): void {
    this.revision++;
    this.deposit.set(null);
    this.pending.set(null);
    this.notice.set('');
    this.loadError.set('');
  }

  refresh(): Observable<DepositView> {
    return defer(() => {
      const revision = this.revision;

      return this.http.get<DepositView>('/api/deposits/current').pipe(
        timeout(8000),
        tap((deposit) => {
          if (revision === this.revision && !this.busy()) {
            this.deposit.set(deposit);
            this.loadError.set('');
            this.restorePending(deposit.id);
          }
        }),
        catchError((error: unknown) => {
          if (revision === this.revision) {
            this.loadError.set('Unable to refresh the deposit. Check the connection.');
          }

          return throwError(() => error);
        }),
      );
    });
  }

  insert(currency: string, denomination: number): Observable<BanknoteResult> {
    return defer(() => {
      const deposit = this.deposit();

      if (!deposit || this.busy() || this.pending()) {
        return EMPTY;
      }

      const pending: PendingBanknote = {
        depositId: deposit.id,
        request: {
          operationId: crypto.randomUUID(),
          currency,
          denomination,
        },
      };

      try {
        sessionStorage.setItem(this.storageKey, JSON.stringify(pending));
      } catch {
        this.notice.set('Unable to preserve the operation. Enable session storage and try again.');
        return EMPTY;
      }

      this.pending.set(pending);

      return this.send(pending, false);
    });
  }

  retry(): Observable<BanknoteResult> {
    return defer(() => {
      const pending = this.pending();

      return pending && !this.busy() ? this.send(pending, true) : EMPTY;
    });
  }

  clear(): void {
    this.open();
    this.removePending();
  }

  private send(pending: PendingBanknote, retrying: boolean): Observable<BanknoteResult> {
    this.revision++;
    this.busy.set(true);
    this.notice.set('');

    return this.csrf.post<BanknoteResult>('/api/deposits/current/banknotes', pending.request).pipe(
      tap((result) => {
        this.revision++;
        this.deposit.set(result.deposit);
        this.removePending();

        this.notice.set(
          result.outcome === 'ACCEPTED'
            ? result.replayed
              ? 'Banknote already recorded. The total has been refreshed.'
              : 'Banknote accepted.'
            : result.outcome === 'CURRENCY_REJECTED'
              ? 'Banknote returned. Only EUR banknotes are accepted.'
              : 'Banknote returned. Unsupported denomination.',
        );
      }),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && [400, 404, 409].includes(error.status)) {
          this.removePending();

          this.notice.set(
            error.status === 404
              ? 'Banknote simulation is unavailable. Start the backend with the simulation profile.'
              : error.status === 409
                ? 'Banknote not accepted. Check the machine status and the active deposit.'
                : 'Invalid banknote request.',
          );
        } else if (error instanceof HttpErrorResponse && error.status === 403 && !retrying) {
          this.removePending();
          this.notice.set('The security check failed. Please try again.');
        } else {
          this.notice.set(
            error instanceof HttpErrorResponse && error.status === 401
              ? 'Your session has ended. Accepted banknotes remain recorded.'
              : 'The outcome is unconfirmed. Check the same operation before inserting another note.',
          );
        }

        return throwError(() => error);
      }),
      finalize(() => this.busy.set(false)),
    );
  }

  private restorePending(depositId: string): void {
    if (this.pending()) {
      return;
    }

    try {
      const stored = sessionStorage.getItem(this.storageKey);

      if (!stored) {
        return;
      }

      const value = JSON.parse(stored) as PendingBanknote;

      if (
        value?.depositId !== depositId ||
        typeof value.request?.operationId !== 'string' ||
        !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(value.request.operationId) ||
        typeof value.request.currency !== 'string' ||
        !/^[A-Z]{3}$/.test(value.request.currency) ||
        !Number.isInteger(value.request.denomination) ||
        value.request.denomination <= 0
      ) {
        this.removePending();
        return;
      }

      this.pending.set(value);
      this.notice.set('An operation needs confirmation. Check it before inserting another note.');
    } catch {
      this.notice.set('Unable to restore the previous operation. Check session storage.');
    }
  }

  private removePending(): void {
    this.pending.set(null);

    try {
      sessionStorage.removeItem(this.storageKey);
    } catch {
      this.notice.set('Unable to clear saved operation data.');
    }
  }
}
