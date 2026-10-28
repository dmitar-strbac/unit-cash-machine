import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BanknoteRequest, DepositView } from './deposit.models';
import { DepositService } from './deposit.service';

describe('DepositService', () => {
  let service: DepositService;
  let http: HttpTestingController;

  const deposit: DepositView = {
    id: '00000000-0000-0000-0000-000000000010',
    status: 'ACTIVE',
    currency: 'EUR',
    totalCents: 0,
    banknoteCount: 0,
    startedAt: '2026-01-01T12:00:00Z',
    expiresAt: '2026-01-01T12:15:00Z',
    finishedAt: null,
    acceptingEnabled: true,
  };

  beforeEach(() => {
    sessionStorage.removeItem('unit.pending-banknote');

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(DepositService);
    http = TestBed.inject(HttpTestingController);

    service.refresh().subscribe();
    http.expectOne('/api/deposits/current').flush(deposit);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.removeItem('unit.pending-banknote');
  });

  function csrf(): void {
    http.expectOne('/api/auth/csrf').flush({
      headerName: 'X-CSRF-TOKEN',
      token: 'test-token',
    });
  }

  it('uses CSRF and displays the total returned by the backend', () => {
    service.insert('EUR', 20).subscribe();

    expect(service.busy()).toBe(true);

    csrf();

    const request = http.expectOne('/api/deposits/current/banknotes');
    const body = request.request.body as BanknoteRequest;

    expect(body.currency).toBe('EUR');
    expect(body.denomination).toBe(20);
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('test-token');

    request.flush({
      operationId: body.operationId,
      outcome: 'ACCEPTED',
      replayed: false,
      deposit: { ...deposit, totalCents: 2000, banknoteCount: 1 },
    });

    expect(service.deposit()?.totalCents).toBe(2000);
    expect(service.busy()).toBe(false);
    expect(service.pending()).toBeNull();
  });

  it('retries an unconfirmed operation with exactly the same identifier', () => {
    service.insert('EUR', 20).subscribe({ error: () => undefined });

    csrf();

    const first = http.expectOne('/api/deposits/current/banknotes');
    const original = first.request.body as BanknoteRequest;

    first.error(new ProgressEvent('error'));

    expect(service.pending()?.request).toEqual(original);

    service.insert('EUR', 50).subscribe();

    http.expectNone('/api/auth/csrf');

    service.retry().subscribe();

    csrf();

    const retry = http.expectOne('/api/deposits/current/banknotes');

    expect(retry.request.body).toEqual(original);

    retry.flush({
      operationId: original.operationId,
      outcome: 'ACCEPTED',
      replayed: true,
      deposit: { ...deposit, totalCents: 2000, banknoteCount: 1 },
    });

    expect(service.deposit()?.banknoteCount).toBe(1);
    expect(service.pending()).toBeNull();
  });

  it('restores an unconfirmed operation after reopening the deposit', () => {
    service.insert('EUR', 20).subscribe({ error: () => undefined });

    csrf();

    const request = http.expectOne('/api/deposits/current/banknotes');
    const original = request.request.body as BanknoteRequest;

    request.error(new ProgressEvent('error'));

    service.open();
    service.refresh().subscribe();

    http.expectOne('/api/deposits/current').flush(deposit);

    expect(service.pending()?.request).toEqual(original);
  });

  it('does not let an old read overwrite an accepted banknote result', () => {
    service.refresh().subscribe();

    const oldRead = http.expectOne('/api/deposits/current');

    service.insert('EUR', 20).subscribe();

    csrf();

    const request = http.expectOne('/api/deposits/current/banknotes');
    const body = request.request.body as BanknoteRequest;

    request.flush({
      operationId: body.operationId,
      outcome: 'ACCEPTED',
      replayed: false,
      deposit: { ...deposit, totalCents: 2000, banknoteCount: 1 },
    });

    oldRead.flush(deposit);

    expect(service.deposit()?.totalCents).toBe(2000);
  });

  it('keeps the total unchanged when another currency is returned', () => {
    service.insert('USD', 20).subscribe();

    csrf();

    const request = http.expectOne('/api/deposits/current/banknotes');
    const body = request.request.body as BanknoteRequest;

    request.flush({
      operationId: body.operationId,
      outcome: 'CURRENCY_REJECTED',
      replayed: false,
      deposit,
    });

    expect(service.deposit()?.totalCents).toBe(0);
    expect(service.notice()).toContain('Only EUR');
    expect(service.pending()).toBeNull();
  });

  it('allows logout after the backend definitively rejects a new banknote', () => {
    service.insert('EUR', 20).subscribe({ error: () => undefined });

    csrf();

    http.expectOne('/api/deposits/current/banknotes').flush(null, {
      status: 409,
      statusText: 'Conflict',
    });

    expect(service.pending()).toBeNull();
    expect(service.busy()).toBe(false);
    expect(service.deposit()?.totalCents).toBe(0);
  });
});
