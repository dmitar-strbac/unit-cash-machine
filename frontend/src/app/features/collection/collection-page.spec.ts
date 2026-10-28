import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../core/auth/auth.service';
import { CollectionView } from '../../core/collection/collection.models';
import { CollectionService } from '../../core/collection/collection.service';
import { CollectionPage } from './collection-page';

describe('CollectionPage', () => {
  const initial: CollectionView = {
    id: '00000000-0000-0000-0000-000000000020',
    step: 'REMOVE_CASSETTE',
    doorClosed: false,
    doorLocked: false,
    removalConfirmed: false,
    insertionConfirmed: false,
    startedAt: '2026-01-01T12:00:00Z',
    securingDeadline: null,
    finishedAt: null,
    collectedTotalCents: null,
    errorCode: null,
  };

  function mocks() {
    return {
      current: vi.fn(() => of(initial)),
      confirmRemoval: vi.fn(() => of(initial)),
      confirmInsertion: vi.fn(() => of(initial)),
      restart: vi.fn(() => of(undefined)),
    };
  }

  let api: ReturnType<typeof mocks>;

  let auth: {
    logout: ReturnType<typeof vi.fn>;
    refresh: ReturnType<typeof vi.fn>;
    clearSession: ReturnType<typeof vi.fn>;
  };

  let fixture: ComponentFixture<CollectionPage> | undefined;

  beforeEach(async () => {
    api = mocks();

    auth = {
      logout: vi.fn(() => of(undefined)),
      refresh: vi.fn(() => of(null)),
      clearSession: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [CollectionPage],
      providers: [
        provideRouter([]),
        { provide: CollectionService, useValue: api },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();

    vi.useFakeTimers();

    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => {
    fixture?.destroy();
    fixture = undefined;
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function render(view = initial): HTMLElement {
    api.current.mockReturnValue(of(view));

    fixture = TestBed.createComponent(CollectionPage);
    fixture.detectChanges();

    vi.advanceTimersByTime(0);
    fixture.detectChanges();

    return fixture.nativeElement as HTMLElement;
  }

  it('shows cassette instructions and prevents duplicate confirmation clicks', () => {
    const response = new Subject<CollectionView>();

    api.confirmRemoval.mockReturnValue(response);

    const element = render();
    const next = element.querySelector<HTMLButtonElement>('.primary-action')!;

    expect(element.querySelector('h1')?.textContent).toContain('Remove cassette');

    next.click();
    fixture!.detectChanges();

    expect(next.disabled).toBe(true);

    next.click();

    expect(api.confirmRemoval).toHaveBeenCalledTimes(1);
    expect(api.confirmInsertion).not.toHaveBeenCalled();

    const inserted: CollectionView = {
      ...initial,
      step: 'INSERT_CASSETTE',
      removalConfirmed: true,
    };

    api.current.mockReturnValue(of(inserted));

    response.next(inserted);
    response.complete();
    fixture!.detectChanges();

    expect(element.querySelector('h1')?.textContent).toContain('Insert NEW cassette');
  });

  it('shows success and closes the session without a logout button', () => {
    const element = render({
      ...initial,
      step: 'COMPLETED',
      doorClosed: true,
      doorLocked: true,
    });

    expect(element.querySelector('h1')?.textContent).toContain('CIT completed');

    expect(element.textContent).not.toContain('LOG OUT');
    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(auth.clearSession).toHaveBeenCalled();

    vi.advanceTimersByTime(4000);

    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/', { replaceUrl: true });
  });

  it('checks the session when the restart response is lost', () => {
    api.restart.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    const element = render({
      ...initial,
      step: 'INCOMPLETE',
      errorCode: 'LOCK_TIMEOUT',
    });

    element.querySelector<HTMLButtonElement>('.primary-action')!.click();

    expect(auth.refresh).toHaveBeenCalledTimes(1);

    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/login/cit', {
      replaceUrl: true,
    });
  });
});
