import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import {
  EMPTY,
  Observable,
  catchError,
  defer,
  finalize,
  switchMap,
  tap,
  throwError,
  timeout,
} from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { CsrfHttpService } from '../http/csrf-http.service';
import {
  PendingPrint,
  PrintResult,
  ReceiptView,
  ReportOverview,
  ReportType,
} from './report.models';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfHttpService);
  private readonly auth = inject(AuthService);
  private readonly storageKey = 'unit.pending-print';

  readonly busy = signal(false);
  readonly pending = signal<PendingPrint | null>(null);
  readonly error = signal('');

  overview(): Observable<ReportOverview> {
    return this.http.get<ReportOverview>('/api/reports').pipe(timeout(8000));
  }

  receipts(page: number): Observable<ReceiptView[]> {
    return this.http.get<ReceiptView[]>('/api/receipts', { params: { page } }).pipe(timeout(8000));
  }

  receipt(id: string): Observable<ReceiptView> {
    return this.http.get<ReceiptView>(`/api/receipts/${id}`).pipe(timeout(8000));
  }

  pdf(id: string): Observable<Blob> {
    return this.http.get(`/api/receipts/${id}/pdf`, { responseType: 'blob' }).pipe(timeout(15000));
  }

  print(type: ReportType): Observable<ReceiptView> {
    return this.begin({ kind: 'report', type, requestId: crypto.randomUUID() });
  }

  reprint(receiptId: string): Observable<ReceiptView> {
    return this.begin({
      kind: 'reprint',
      receiptId,
      requestId: crypto.randomUUID(),
    });
  }

  retry(): Observable<ReceiptView> {
    return defer(() => {
      const command = this.pending();
      return command ? this.send(command) : EMPTY;
    });
  }

  restore(): void {
    this.pending.set(null);
    this.error.set('');

    try {
      const stored = sessionStorage.getItem(this.storageKey);

      if (!stored) {
        return;
      }

      const value = JSON.parse(stored) as {
        owner?: string;
        command?: Partial<PendingPrint>;
      };

      const command = value.command;
      const validId = (id: unknown): id is string =>
        typeof id === 'string' && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id);

      const valid =
        value.owner === this.owner() &&
        validId(command?.requestId) &&
        ((command?.kind === 'report' &&
          ['DEVICE_TOTAL', 'DAY_TOTAL', 'DAY_TOTAL_USER'].includes(command.type ?? '')) ||
          (command?.kind === 'reprint' && validId(command.receiptId)));

      if (valid) {
        this.pending.set(command as PendingPrint);
      } else {
        sessionStorage.removeItem(this.storageKey);
      }
    } catch {
      this.error.set('Unable to restore the last print request. Check session storage.');
    }
  }

  private begin(command: PendingPrint): Observable<ReceiptView> {
    return defer(() => {
      if (this.busy() || this.pending()) {
        return EMPTY;
      }

      try {
        sessionStorage.setItem(this.storageKey, JSON.stringify({ owner: this.owner(), command }));
      } catch {
        this.error.set('Unable to preserve the print request. Enable session storage.');
        return EMPTY;
      }

      this.pending.set(command);
      return this.send(command);
    });
  }

  private send(command: PendingPrint): Observable<ReceiptView> {
    return defer(() => {
      if (this.busy()) {
        return EMPTY;
      }

      const owner = this.owner();
      this.busy.set(true);
      this.error.set('');

      const request =
        command.kind === 'report'
          ? this.csrf.post<ReceiptView>(`/api/reports/${command.type}/print`, {
              requestId: command.requestId,
            })
          : this.csrf
              .post<PrintResult>(`/api/receipts/${command.receiptId}/print`, {
                requestId: command.requestId,
              })
              .pipe(switchMap(() => this.receipt(command.receiptId)));

      return request.pipe(
        tap(() => {
          if (owner === this.owner()) {
            this.clearPending();
          }
        }),
        catchError((error: unknown) => {
          if (owner === this.owner()) {
            const terminal =
              error instanceof HttpErrorResponse && [400, 403, 404, 409].includes(error.status);

            if (terminal) {
              this.clearPending();
            }

            this.error.set(
              terminal
                ? 'The print request was rejected. Refresh and check access or security.'
                : 'The print result is unconfirmed. Check the same request before printing again.',
            );
          }

          return throwError(() => error);
        }),
        finalize(() => this.busy.set(false)),
      );
    });
  }

  private owner(): string {
    const user = this.auth.user();
    return user ? `${user.id}:${user.expiresAt}` : '';
  }

  private clearPending(): void {
    this.pending.set(null);

    try {
      sessionStorage.removeItem(this.storageKey);
    } catch {
      this.error.set('The result is confirmed, but saved request data could not be cleared.');
    }
  }
}
