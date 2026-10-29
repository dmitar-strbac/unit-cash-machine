import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';
import { SessionCoordinator } from './core/auth/session-coordinator.service';
import { MachineStatus } from './core/machine/machine-status';
import { MachineConnection, MachineStatusService } from './core/machine/machine-status.service';
import { MaintenanceService } from './core/maintenance/maintenance.service';

describe('App', () => {
  let connection: Subject<MachineConnection>;

  const healthyStatus: MachineStatus = {
    machineCode: 'UNIT-SIM-001',
    displayName: 'Unit Cash Machine',
    location: 'Local development environment',
    currency: 'EUR',
    timeZone: 'Europe/Belgrade',
    simulated: true,
    secure: true,
    depositHardwareReady: true,
    printingAvailable: true,
    hardware: {
      acceptor: 'READY',
      cashbox: 'PRESENT',
      door: 'CLOSED',
      lock: 'LOCKED',
      extensionBoard: 'ONLINE',
      printer: 'READY',
    },
    indicator: 'GREEN',
    alertCount: 0,
    alerts: [],
    runtime: {
      state: 'RUNNING',
      generation: 0,
      commandId: null,
      action: null,
      completesAt: null,
      notice: null,
    },
  };

  beforeEach(async () => {
    connection = new Subject<MachineConnection>();

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        {
          provide: MaintenanceService,
          useValue: {
            runtime: signal(healthyStatus.runtime),
            blocked: signal(false),
            disconnected: signal(false),
          },
        },
        {
          provide: SessionCoordinator,
          useValue: {
            timedOut: signal(false),
            ending: signal(false),
            error: signal(''),
            logout: vi.fn(),
          },
        },
        {
          provide: MachineStatusService,
          useValue: {
            connection$: connection.asObservable(),
          },
        },
        {
          provide: AuthService,
          useValue: {
            user: signal(null),
            notice: signal(''),
            refresh: () => of(null),
          },
        },
      ],
    }).compileComponents();
  });

  it('shows an unknown indicator before the first successful response', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;

    expect(element.textContent).toContain('Connecting…');
    expect(element.querySelector('.status-dot.unknown')).not.toBeNull();
    expect(element.querySelector('.status-dot.green')).toBeNull();
  });

  it('replaces a healthy indicator when the connection is lost and recovers afterward', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;

    connection.next({ state: 'connected', status: healthyStatus });
    fixture.detectChanges();

    expect(element.querySelector('.status-dot.green')).not.toBeNull();

    connection.next({ state: 'disconnected', status: null });
    fixture.detectChanges();

    expect(element.textContent).toContain('Connection lost');
    expect(element.querySelector('.status-dot.green')).toBeNull();
    expect(element.querySelector('.status-dot.unknown')).not.toBeNull();

    connection.next({ state: 'connected', status: healthyStatus });
    fixture.detectChanges();

    expect(element.textContent).toContain('No alerts');
    expect(element.querySelector('.status-dot.green')).not.toBeNull();
  });

  it('shows an amber indicator and alert count for a printer warning', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    connection.next({
      state: 'connected',
      status: {
        ...healthyStatus,
        printingAvailable: false,
        hardware: { ...healthyStatus.hardware, printer: 'OFFLINE' },
        indicator: 'AMBER',
        alertCount: 1,
        alerts: [
          {
            code: 'PRINTER_OFFLINE',
            source: 'PRINTER',
            severity: 'WARNING',
            message: 'Not operational',
            blocksDeposit: false,
          },
        ],
      },
    });

    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;

    expect(element.textContent).toContain('1 alert');
    expect(element.querySelector('.status-dot.amber')).not.toBeNull();
  });
});
