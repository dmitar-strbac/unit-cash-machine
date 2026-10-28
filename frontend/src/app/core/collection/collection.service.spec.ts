import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CollectionService } from './collection.service';

describe('CollectionService', () => {
  let service: CollectionService;
  let http: HttpTestingController;

  const id = '00000000-0000-0000-0000-000000000020';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(CollectionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function csrf(): void {
    http.expectOne('/api/auth/csrf').flush({
      headerName: 'X-CSRF-TOKEN',
      token: 'cit-token',
    });
  }

  it('reads the current procedure', () => {
    service.current().subscribe();

    const request = http.expectOne('/api/collections/current');

    expect(request.request.method).toBe('GET');

    request.flush({ id, step: 'OPEN_DOOR' });
  });

  it('uses separate CSRF-protected confirmation endpoints', () => {
    service.confirmRemoval(id).subscribe();
    csrf();

    const removal = http.expectOne(`/api/collections/${id}/confirm-removal`);

    expect(removal.request.method).toBe('POST');
    expect(removal.request.headers.get('X-CSRF-TOKEN')).toBe('cit-token');

    removal.flush({ id, step: 'INSERT_CASSETTE' });

    service.confirmInsertion(id).subscribe();
    csrf();

    const insertion = http.expectOne(`/api/collections/${id}/confirm-insertion`);

    expect(insertion.request.headers.get('X-CSRF-TOKEN')).toBe('cit-token');

    insertion.flush({ id, step: 'CLOSE_DOOR' });
  });

  it('sends a physical action for the selected procedure', () => {
    service.simulate(id, 'LOCK_DOOR').subscribe();
    csrf();

    const request = http.expectOne(`/api/collections/${id}/simulate`);

    expect(request.request.body).toEqual({ action: 'LOCK_DOOR' });
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('cit-token');

    request.flush({ door: 'CLOSED', lock: 'LOCKED' });
  });

  it('restarts the selected procedure using CSRF', () => {
    service.restart(id).subscribe();
    csrf();

    const request = http.expectOne(`/api/collections/${id}/restart`);

    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('cit-token');

    request.flush(null, {
      status: 204,
      statusText: 'No Content',
    });
  });
});
