import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { EMPTY, Observable, catchError, exhaustMap, filter, timer } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { BanknoteResult } from '../../core/deposit/deposit.models';
import { DepositService } from '../../core/deposit/deposit.service';
import { MachineStatusService } from '../../core/machine/machine-status.service';

@Component({
  selector: 'app-deposit-panel',
  imports: [DecimalPipe],
  templateUrl: './deposit-panel.html',
  styleUrl: './deposit-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DepositPanel {
  readonly ending = input(false);

  protected readonly deposits = inject(DepositService);

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly connection = toSignal(inject(MachineStatusService).connection$);

  protected readonly denominations = [5, 10, 20, 50, 100, 200, 500];
  protected readonly currencies = ['EUR', 'USD', 'GBP', 'RSD'];

  protected readonly denomination = signal(20);
  protected readonly currency = signal('EUR');

  protected readonly accepting = computed(() => {
    const connection = this.connection();

    return (
      connection?.state === 'connected' &&
      connection.status.depositHardwareReady &&
      this.deposits.deposit()?.acceptingEnabled === true &&
      !this.deposits.loadError() &&
      !this.deposits.busy() &&
      !this.deposits.pending() &&
      !this.ending()
    );
  });

  constructor() {
    this.deposits.open();

    timer(0, 3000)
      .pipe(
        filter(() => !this.deposits.busy() && !this.ending()),
        exhaustMap(() =>
          this.deposits.refresh().pipe(
            catchError((error: unknown) => {
              this.checkSession(error);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  protected selectCurrency(event: Event): void {
    this.currency.set((event.target as HTMLSelectElement).value);
  }

  protected selectDenomination(event: Event): void {
    this.denomination.set(Number((event.target as HTMLSelectElement).value));
  }

  protected insert(): void {
    if (this.accepting()) {
      this.submit(this.deposits.insert(this.currency(), this.denomination()));
    }
  }

  protected retry(): void {
    if (!this.ending()) {
      this.submit(this.deposits.retry());
    }
  }

  private submit(request: Observable<BanknoteResult>): void {
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      error: (error: unknown) => this.checkSession(error),
    });
  }

  private checkSession(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      this.auth
        .refresh()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (user) => {
            if (!user) {
              void this.router.navigateByUrl('/', { replaceUrl: true });
            }
          },
          error: () => undefined,
        });
    }
  }
}
