import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { CashierReceiptService } from '../../core/report/cashier-receipt.service';
import { ReceiptView } from '../../core/report/report.models';
import { ReportService } from '../../core/report/report.service';

@Component({
  selector: 'app-receipt-preview',
  imports: [DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-label="Virtual receipt">
      <p class="receipt-status" [class.failed]="receipt().printStatus === 'FAILED'">
        @if (receipt().printStatus === 'PRINTED') {
          Printed by the virtual printer.
        } @else if (receipt().printStatus === 'FAILED') {
          Print failed: {{ receipt().printError }}. The receipt remains saved.
        } @else {
          Receipt saved.
        }
      </p>

      <article class="paper">
        <pre>{{ receipt().content }}</pre>
      </article>

      <div class="download">
        <button class="machine-button" [disabled]="busy()" (click)="download()">
          {{ busy() ? 'PREPARING…' : 'DOWNLOAD PDF' }}
        </button>
      </div>

      @if (error()) {
        <p role="alert">{{ error() }}</p>
      }
    </section>
  `,
  styles: `
    .paper {
      width: min(100%, 390px);
      margin: 24px auto;
      padding: 28px 24px;
      background: #fffefa;
      color: #242424;
      box-shadow: 0 5px 18px #0002;
      border-bottom: 5px dotted var(--surface);
    }

    pre {
      margin: 0;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
      font:
        14px/1.65 'Courier New',
        monospace;
    }

    .receipt-status,
    [role='alert'] {
      text-align: center;
    }

    .failed,
    [role='alert'] {
      color: #a51e37;
    }

    .download {
      display: flex;
      justify-content: center;
    }

    button {
      min-height: 56px;
      padding: 12px 24px;
    }
  `,
})
export class ReceiptPreview {
  readonly receipt = input.required<ReceiptView>();
  readonly cashier = input(false);

  private readonly reports = inject(ReportService);
  private readonly access = inject(CashierReceiptService);

  protected readonly busy = signal(false);
  protected readonly error = signal('');

  protected download(): void {
    if (this.busy()) {
      return;
    }

    this.busy.set(true);
    this.error.set('');

    const request = this.cashier() ? this.access.pdf() : this.reports.pdf(this.receipt().id);

    request.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');

        link.href = url;
        link.download = `receipt-${this.receipt().id}.pdf`;
        link.click();

        setTimeout(() => URL.revokeObjectURL(url), 60000);
      },
      error: () => {
        this.error.set('Unable to download the PDF. Check the session or receipt access.');
      },
    });
  }
}
