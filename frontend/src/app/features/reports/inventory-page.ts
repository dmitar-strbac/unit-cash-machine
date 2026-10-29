import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError, exhaustMap, filter, timer } from 'rxjs';
import { ReceiptView, ReportOverview, ReportType } from '../../core/report/report.models';
import { ReportService } from '../../core/report/report.service';
import { ReceiptPreview } from '../printing/receipt-preview';

@Component({
  selector: 'app-inventory-page',
  imports: [DecimalPipe, RouterLink, ReceiptPreview],
  templateUrl: './inventory-page.html',
  styleUrl: './inventory-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryPage {
  protected readonly api = inject(ReportService);
  private readonly route = inject(ActivatedRoute);

  protected readonly overview = signal<ReportOverview | null>(null);
  protected readonly loadError = signal('');
  protected readonly receipt = signal<ReceiptView | null>(null);

  protected readonly detail = this.route.snapshot.data['reportType'] as ReportType | undefined;

  protected readonly selected = computed(() => {
    const overview = this.overview();

    return this.detail === 'DEVICE_TOTAL' ? overview?.deviceTotal : overview?.dayTotal;
  });

  protected readonly unavailable = computed(
    () => !this.overview() || !!this.loadError() || this.api.busy() || !!this.api.pending(),
  );

  constructor() {
    this.api.restore();

    timer(0, 5000)
      .pipe(
        filter(() => !this.api.busy()),
        exhaustMap(() =>
          this.api.overview().pipe(
            catchError(() => {
              this.loadError.set('Unable to refresh totals. Check the connection.');
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((overview) => {
        this.overview.set(overview);
        this.loadError.set('');
      });
  }

  protected print(type: ReportType): void {
    if (!this.unavailable()) {
      this.submit(this.api.print(type));
    }
  }

  protected retry(): void {
    this.submit(this.api.retry());
  }

  private submit(request: Observable<ReceiptView>): void {
    request.subscribe({
      next: (receipt) => this.receipt.set(receipt),
      error: () => undefined,
    });
  }
}
