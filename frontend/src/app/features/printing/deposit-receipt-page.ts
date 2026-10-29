import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize, retry, throwError, timer } from 'rxjs';
import { CashierReceiptService } from '../../core/report/cashier-receipt.service';
import { ReceiptView } from '../../core/report/report.models';
import { ReceiptPreview } from './receipt-preview';

@Component({
  selector: 'app-deposit-receipt-page',
  imports: [RouterLink, ReceiptPreview],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Deposit receipt</h1>
    <p>Your session has ended. Accepted money remains recorded.</p>

    @if (loading()) {
      <p role="status">Waiting for the saved receipt…</p>
    }

    @if (error()) {
      <p role="alert">{{ error() }}</p>
      <button class="machine-button action" [disabled]="loading()" (click)="load()">
        CHECK RECEIPT
      </button>
    }

    @if (receipt(); as current) {
      <app-receipt-preview [receipt]="current" [cashier]="true" />
    }

    <a class="machine-button action" routerLink="/">HOME</a>
  `,
  styles: `
    .action {
      width: fit-content;
      min-height: 54px;
      margin: 20px auto;
      padding: 12px 30px;
    }

    [role='alert'] {
      color: #a51e37;
    }
  `,
})
export class DepositReceiptPage {
  private readonly api = inject(CashierReceiptService);

  protected readonly receipt = signal<ReceiptView | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal('');

  constructor() {
    this.load();
  }

  protected load(): void {
    if (this.loading()) {
      return;
    }

    if (!this.api.access()) {
      this.error.set('Receipt access is unavailable. Management can retrieve the saved receipt.');
      return;
    }

    this.loading.set(true);
    this.error.set('');

    this.api
      .receipt()
      .pipe(
        retry({
          count: 5,
          delay: (error: unknown) =>
            error instanceof HttpErrorResponse && error.status === 404
              ? timer(1000)
              : throwError(() => error),
        }),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (receipt) => this.receipt.set(receipt),
        error: () => {
          this.error.set(
            'No receipt is available yet, access has expired, or no banknotes were accepted. Management can check saved records.',
          );
        },
      });
  }
}
