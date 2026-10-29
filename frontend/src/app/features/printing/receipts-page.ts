import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { ReceiptView } from '../../core/report/report.models';
import { ReportService } from '../../core/report/report.service';
import { ReceiptPreview } from './receipt-preview';

@Component({
  selector: 'app-receipts-page',
  imports: [DatePipe, DecimalPipe, ReceiptPreview],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Virtual printer</h1>

    @if (error()) {
      <p role="alert">{{ error() }}</p>
    }

    <div class="toolbar">
      <button class="machine-button" [disabled]="loading()" (click)="load()">REFRESH</button>
      <button class="machine-button" [disabled]="loading() || page() === 0" (click)="change(-1)">
        PREVIOUS
      </button>
      <button
        class="machine-button"
        [disabled]="loading() || rows().length < 50"
        (click)="change(1)"
      >
        NEXT
      </button>
    </div>

    <div class="receipts">
      @for (row of rows(); track row.id) {
        <button class="machine-button receipt-row" (click)="selected.set(row)">
          <span>{{ row.createdAt | date: 'dd.MM.yyyy HH:mm:ss' }}</span>
          <strong>{{ row.kind.replaceAll('_', ' ') }}</strong>
          <span>EUR {{ row.totalCents / 100 | number: '1.2-2' }}</span>
          <span [class.failed]="row.printStatus === 'FAILED'">{{ row.printStatus }}</span>
        </button>
      } @empty {
        <p>{{ loading() ? 'Loading receipts…' : 'No saved receipts on this page.' }}</p>
      }
    </div>

    @if (api.error()) {
      <p role="alert">{{ api.error() }}</p>
    }

    @if (api.pending()) {
      <button class="machine-button action" [disabled]="api.busy()" (click)="retry()">
        CHECK LAST PRINT REQUEST
      </button>
    }

    @if (selected(); as receipt) {
      <app-receipt-preview [receipt]="receipt" />

      <button
        class="machine-button action"
        [disabled]="api.busy() || !!api.pending()"
        (click)="reprint(receipt.id)"
      >
        PRINT ANOTHER COPY
      </button>
    }
  `,
  styles: `
    .toolbar {
      display: flex;
      gap: 12px;
      margin-bottom: 20px;
    }

    .toolbar button,
    .action {
      min-height: 54px;
      padding: 12px 18px;
    }

    .receipt-row {
      display: grid;
      grid-template-columns: 1.4fr 1.3fr 1fr 1fr;
      gap: 14px;
      width: 100%;
      min-height: 76px;
      padding: 14px;
      margin-bottom: 10px;
      text-align: left;
    }

    .failed,
    [role='alert'] {
      color: #a51e37;
    }

    .action {
      margin: 20px auto;
    }

    @media (max-width: 700px) {
      .receipt-row {
        grid-template-columns: 1fr 1fr;
      }
    }
  `,
})
export class ReceiptsPage {
  protected readonly api = inject(ReportService);

  protected readonly rows = signal<ReceiptView[]>([]);
  protected readonly selected = signal<ReceiptView | null>(null);
  protected readonly page = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal('');

  constructor() {
    this.api.restore();
    this.load();
  }

  protected load(): void {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.error.set('');

    this.api
      .receipts(this.page())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (rows) => this.rows.set(rows),
        error: () => this.error.set('Unable to load receipts. Check the connection and access.'),
      });
  }

  protected change(direction: number): void {
    if (!this.loading()) {
      this.page.update((page) => Math.max(0, page + direction));
      this.load();
    }
  }

  protected reprint(id: string): void {
    this.api.reprint(id).subscribe({
      next: (receipt) => this.confirmed(receipt),
      error: () => undefined,
    });
  }

  protected retry(): void {
    this.api.retry().subscribe({
      next: (receipt) => this.confirmed(receipt),
      error: () => undefined,
    });
  }

  private confirmed(receipt: ReceiptView): void {
    this.selected.set(receipt);
    this.load();
  }
}
