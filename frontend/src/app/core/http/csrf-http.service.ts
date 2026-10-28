import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, switchMap, timeout } from 'rxjs';
import { CsrfResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class CsrfHttpService {
  private readonly http = inject(HttpClient);

  post<T>(url: string, body: unknown): Observable<T> {
    return this.http.get<CsrfResponse>('/api/auth/csrf').pipe(
      timeout(8000),
      switchMap((csrf) =>
        this.http
          .post<T>(url, body, {
            headers: { [csrf.headerName]: csrf.token },
          })
          .pipe(timeout(8000)),
      ),
    );
  }
}
