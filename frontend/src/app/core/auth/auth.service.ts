import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap, throwError, timeout } from 'rxjs';
import { CsrfHttpService } from '../http/csrf-http.service';
import { AuthenticatedUser, UserRole, UserSummary } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfHttpService);
  private readonly currentUser = signal<AuthenticatedUser | null>(null);

  private sessionRevision = 0;

  readonly user = this.currentUser.asReadonly();
  readonly notice = signal('');

  users(role: UserRole): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>('/api/users', { params: { role } }).pipe(timeout(8000));
  }

  refresh(): Observable<AuthenticatedUser | null> {
    const revision = this.sessionRevision;

    return this.http.get<AuthenticatedUser>('/api/auth/me').pipe(
      timeout(8000),
      tap((user) => {
        if (revision === this.sessionRevision) {
          this.currentUser.set(user);
          this.notice.set('');
        }
      }),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          if (revision === this.sessionRevision) {
            if (this.currentUser()) {
              this.notice.set('Your session has ended. Please sign in again.');
            }

            this.currentUser.set(null);
          }

          return of(null);
        }

        if (revision === this.sessionRevision) {
          this.notice.set('Unable to verify the session. Check the connection and try again.');
        }

        return throwError(() => error);
      }),
    );
  }

  login(role: UserRole, userId: string | null, pin: string): Observable<AuthenticatedUser> {
    const endpoint = role === 'CIT' ? '/api/auth/cit-login' : '/api/auth/login';
    const body = role === 'CIT' ? { pin } : { userId, role, pin };

    return this.postWithCsrf<AuthenticatedUser>(endpoint, body).pipe(
      tap((user) => {
        this.sessionRevision++;
        this.currentUser.set(user);
        this.notice.set('');
      }),
    );
  }

  logout(): Observable<void> {
    return this.postWithCsrf<void>('/api/auth/logout', {}).pipe(
      tap(() => {
        this.sessionRevision++;
        this.currentUser.set(null);
        this.notice.set('');
      }),
    );
  }

  clearSession(message = ''): void {
    this.sessionRevision++;
    this.currentUser.set(null);
    this.notice.set(message);
  }

  private postWithCsrf<T>(url: string, body: unknown): Observable<T> {
    return this.csrf.post<T>(url, body);
  }
}
