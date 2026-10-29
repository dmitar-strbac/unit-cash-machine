import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../auth/auth.service';
import { CsrfHttpService } from '../http/csrf-http.service';
import { ReceiptView } from './report.models';
import { ReportService } from './report.service';

describe('ReportService', () => {
  const receipt: ReceiptView = {
    id: '00000000-0000-0000-0000-000000000001',
    kind: 'DAY_TOTAL',
    requestedBy: 'manager',
    createdAt: '2026-01-01T12:00:00Z',
    totalCents: 2000,
    content: 'Total EUR: 20.00',
    printStatus: 'PRINTED',
    printError: null,
  };

  beforeEach(() => {
    sessionStorage.clear();
  });

  it('retries an uncertain print with the original request identifier', () => {
    const post = vi
      .fn()
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })))
      .mockReturnValueOnce(of(receipt));

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        {
          provide: AuthService,
          useValue: {
            user: signal({
              id: 'manager',
              expiresAt: '2026-01-01T12:15:00Z',
            }),
          },
        },
        { provide: CsrfHttpService, useValue: { post } },
      ],
    });

    const service = TestBed.inject(ReportService);

    service.print('DAY_TOTAL').subscribe({ error: () => undefined });

    expect(service.pending()).not.toBeNull();

    const firstId = service.pending()?.requestId;

    service.retry().subscribe();

    expect(post).toHaveBeenCalledTimes(2);
    expect(post.mock.calls[0]?.[1]).toEqual({ requestId: firstId });
    expect(post.mock.calls[1]?.[1]).toEqual({ requestId: firstId });
    expect(service.pending()).toBeNull();
  });

  it('does not start another print while an uncertain request exists', () => {
    const post = vi.fn().mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        {
          provide: AuthService,
          useValue: {
            user: signal({ id: 'manager', expiresAt: '2026-01-01T12:15:00Z' }),
          },
        },
        { provide: CsrfHttpService, useValue: { post } },
      ],
    });

    const service = TestBed.inject(ReportService);

    service.print('DAY_TOTAL').subscribe({ error: () => undefined });
    service.print('DEVICE_TOTAL').subscribe();

    expect(post).toHaveBeenCalledTimes(1);
  });
});
