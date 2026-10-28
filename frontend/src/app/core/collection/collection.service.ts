import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import { CsrfHttpService } from '../http/csrf-http.service';
import { HardwareSnapshot } from '../machine/machine-status';
import { CollectionView, PhysicalAction } from './collection.models';

@Injectable({ providedIn: 'root' })
export class CollectionService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfHttpService);

  current(): Observable<CollectionView> {
    return this.http.get<CollectionView>('/api/collections/current').pipe(timeout(5000));
  }

  confirmRemoval(id: string): Observable<CollectionView> {
    return this.csrf.post<CollectionView>(`/api/collections/${id}/confirm-removal`, {});
  }

  confirmInsertion(id: string): Observable<CollectionView> {
    return this.csrf.post<CollectionView>(`/api/collections/${id}/confirm-insertion`, {});
  }

  simulate(id: string, action: PhysicalAction): Observable<HardwareSnapshot> {
    return this.csrf.post<HardwareSnapshot>(`/api/collections/${id}/simulate`, { action });
  }

  restart(id: string): Observable<void> {
    return this.csrf.post<void>(`/api/collections/${id}/restart`, {});
  }
}
