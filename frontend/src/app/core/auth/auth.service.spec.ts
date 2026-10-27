import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthenticatedUser } from './auth.models';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  const cashier: AuthenticatedUser = {
    id: '00000000-0000-0000-0000-000000000001',
    displayName: 'Demo Cashier',
    role: 'CASHIER',
    expiresAt: '2026-01-01T12:15:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  function establishSession(): void {
    service.refresh().subscribe();
    http.expectOne('/api/auth/me').flush(cashier);
  }

  function flushCsrf(token: string): void {
    http.expectOne('/api/auth/csrf').flush({
      headerName: 'X-CSRF-TOKEN',
      token,
    });
  }

  it('loads users for the selected role', () => {
    service.users('MANAGEMENT').subscribe((users) => {
      expect(users).toEqual([]);
    });

    const request = http.expectOne(
      (req) => req.url === '/api/users' && req.params.get('role') === 'MANAGEMENT',
    );

    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('logs in a cashier with a CSRF token and keeps the returned user', () => {
    service.login('CASHIER', cashier.id, '1111').subscribe();

    flushCsrf('login-token');

    const request = http.expectOne('/api/auth/login');

    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('login-token');
    expect(request.request.body).toEqual({
      userId: cashier.id,
      role: 'CASHIER',
      pin: '1111',
    });

    request.flush(cashier);

    expect(service.user()).toEqual(cashier);
  });

  it('sends only the PIN for CIT login', () => {
    service.login('CIT', null, '4444').subscribe();

    flushCsrf('cit-token');

    const request = http.expectOne('/api/auth/cit-login');

    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('cit-token');
    expect(request.request.body).toEqual({ pin: '4444' });

    request.flush({ ...cashier, role: 'CIT', displayName: 'Demo CIT' });

    expect(service.user()?.role).toBe('CIT');
  });

  it('gets a CSRF token for logout and clears the user after success', () => {
    establishSession();

    service.logout().subscribe();

    flushCsrf('logout-token');

    const request = http.expectOne('/api/auth/logout');

    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('logout-token');
    expect(service.user()).toEqual(cashier);

    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(service.user()).toBeNull();
  });

  it('clears an expired session when the backend returns 401', () => {
    establishSession();

    service.refresh().subscribe((user) => {
      expect(user).toBeNull();
    });

    http.expectOne('/api/auth/me').flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });

    expect(service.user()).toBeNull();
    expect(service.notice()).toContain('session has ended');
  });

  it('does not treat a connection failure as a successful logout', () => {
    establishSession();

    service.refresh().subscribe({
      error: (error: unknown) => {
        expect(error).toBeInstanceOf(HttpErrorResponse);
      },
    });

    http.expectOne('/api/auth/me').error(new ProgressEvent('error'));

    expect(service.user()).toEqual(cashier);
    expect(service.notice()).toContain('Unable to verify');
  });

  it('does not clear the user when logout fails', () => {
    establishSession();

    service.logout().subscribe({
      error: (error: unknown) => {
        expect(error).toBeInstanceOf(HttpErrorResponse);
      },
    });

    flushCsrf('logout-token');

    http.expectOne('/api/auth/logout').flush(null, {
      status: 403,
      statusText: 'Forbidden',
    });

    expect(service.user()).toEqual(cashier);
  });

  it('ignores an old session response received after logout', () => {
    establishSession();

    service.refresh().subscribe();
    const oldRefresh = http.expectOne('/api/auth/me');

    service.logout().subscribe();
    flushCsrf('logout-token');

    http.expectOne('/api/auth/logout').flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    oldRefresh.flush(cashier);

    expect(service.user()).toBeNull();
  });
});
