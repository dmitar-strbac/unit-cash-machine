import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthenticatedUser } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { CsrfHttpService } from '../http/csrf-http.service';
import { MachineRuntimeView, MaintenanceCommand } from './maintenance.models';
import { MaintenanceService } from './maintenance.service';

describe('MaintenanceService', () => {
  const running: MachineRuntimeView = {
    state: 'RUNNING',
    generation: 0,
    commandId: null,
    action: null,
    completesAt: null,
    notice: null,
  };

  let service: MaintenanceService;
  let http: HttpTestingController;
  let result: Subject<MaintenanceCommand>;
  let post: ReturnType<typeof vi.fn>;
  let clearSession: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();

    result = new Subject<MaintenanceCommand>();
    post = vi.fn(() => result.asObservable());

    const user = signal<AuthenticatedUser | null>({
      id: 'manager',
      displayName: 'Manager',
      role: 'MANAGEMENT',
      expiresAt: '2099-01-01T00:00:00Z',
    });

    clearSession = vi.fn(() => user.set(null));

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CsrfHttpService, useValue: { post } },
        { provide: AuthService, useValue: { user, clearSession } },
        {
          provide: Router,
          useValue: { url: '/system', navigateByUrl: vi.fn() },
        },
      ],
    });

    service = TestBed.inject(MaintenanceService);
    http = TestBed.inject(HttpTestingController);

    vi.advanceTimersByTime(0);
    http.expectOne('/api/machine/runtime').flush(running);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  function accept(action: MaintenanceCommand['action']): void {
    result.next({
      id: 'command-1',
      action,
      requestedBy: 'manager',
      status: 'PENDING',
      requestedAt: '2026-01-01T12:00:00Z',
      completesAt: '2026-01-01T12:00:03Z',
      finishedAt: null,
    });
  }

  it('keeps the operator signed in during reset', () => {
    service.execute('RESET', 'command-1').subscribe();
    accept('RESET');

    http.expectOne('/api/machine/runtime').flush({
      ...running,
      state: 'RESETTING',
      commandId: 'command-1',
    });

    expect(service.runtime()?.state).toBe('RESETTING');
    expect(service.blocked()).toBe(false);
    expect(clearSession).not.toHaveBeenCalled();

    expect(post).toHaveBeenCalledWith('/api/maintenance/commands', {
      requestId: 'command-1',
      action: 'RESET',
    });
  });

  it('reboot removes the old session and blocks the application', () => {
    service.execute('REBOOT', 'command-1').subscribe();
    accept('REBOOT');

    http.expectOne('/api/machine/runtime').flush({
      ...running,
      state: 'REBOOTING',
      generation: 1,
      commandId: 'command-1',
    });

    expect(service.blocked()).toBe(true);
    expect(clearSession).toHaveBeenCalledOnce();

    vi.advanceTimersByTime(1000);

    http.expectOne('/api/machine/runtime').flush({
      ...running,
      generation: 1,
      commandId: 'command-1',
    });

    expect(service.blocked()).toBe(false);
  });

  it('ignores a runtime response started before the command was accepted', () => {
    vi.advanceTimersByTime(1000);

    const stale = http.expectOne('/api/machine/runtime');

    service.execute('RESET', 'command-1').subscribe();
    accept('RESET');
    stale.flush(running);

    expect(service.awaiting()).toBe(true);
    expect(service.operational()).toBe(false);

    vi.advanceTimersByTime(1000);

    http.expectOne('/api/machine/runtime').flush({
      ...running,
      state: 'RESETTING',
      commandId: 'command-1',
    });

    expect(service.runtime()?.state).toBe('RESETTING');
  });

  it('uses the simulator endpoint to power on a stopped machine', () => {
    vi.advanceTimersByTime(1000);

    http.expectOne('/api/machine/runtime').flush({
      ...running,
      generation: 1,
      state: 'SHUT_DOWN',
    });

    expect(service.blocked()).toBe(true);

    service.execute('POWER_ON', 'command-1').subscribe();
    accept('POWER_ON');

    expect(post).toHaveBeenCalledWith('/api/simulator/power-on', {
      requestId: 'command-1',
      action: 'POWER_ON',
    });

    http.expectOne('/api/machine/runtime').flush({
      ...running,
      generation: 2,
      commandId: 'command-1',
    });
  });
});
