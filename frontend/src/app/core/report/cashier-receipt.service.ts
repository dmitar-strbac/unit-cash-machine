import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, of, tap, timeout } from 'rxjs';
import { CsrfHttpService } from '../http/csrf-http.service';
import { ReceiptAccess, ReceiptView } from './report.models';

@Injectable({ providedIn: 'root' })
export class CashierReceiptService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfHttpService);
  private readonly storageKey = 'unit.cashier-receipt';

  readonly access = signal<ReceiptAccess | null>(this.restore());

  prepare(owner: string): Observable<ReceiptAccess> {
    const current = this.access();

    if (current && this.owner() === owner && Date.parse(current.expiresAt) > Date.now()) {
      return of(current);
    }

    return this.csrf.post<ReceiptAccess>('/api/deposits/current/receipt-access', {}).pipe(
      tap((access) => {
        this.access.set(access);

        try {
          sessionStorage.setItem(this.storageKey, JSON.stringify({ owner, access }));
        } catch {
          // The token remains available for the current page lifetime.
        }
      }),
    );
  }

  receipt(): Observable<ReceiptView> {
    return this.http
      .get<ReceiptView>('/api/receipt-access', {
        headers: { 'X-Receipt-Token': this.access()?.token ?? '' },
      })
      .pipe(timeout(8000));
  }

  pdf(): Observable<Blob> {
    return this.http
      .get('/api/receipt-access/pdf', {
        headers: { 'X-Receipt-Token': this.access()?.token ?? '' },
        responseType: 'blob',
      })
      .pipe(timeout(15000));
  }

  clear(): void {
    this.access.set(null);

    try {
      sessionStorage.removeItem(this.storageKey);
    } catch {
      return;
    }
  }

  private owner(): string {
    try {
      const value = JSON.parse(sessionStorage.getItem(this.storageKey) ?? '{}') as {
        owner?: unknown;
      };

      return typeof value.owner === 'string' ? value.owner : '';
    } catch {
      return '';
    }
  }

  private restore(): ReceiptAccess | null {
    try {
      const value = JSON.parse(sessionStorage.getItem(this.storageKey) ?? '{}') as {
        access?: ReceiptAccess;
      };

      const access = value.access;

      return access &&
        /^[a-f0-9]{64}$/.test(access.token) &&
        Date.parse(access.expiresAt) > Date.now()
        ? access
        : null;
    } catch {
      return null;
    }
  }
}
