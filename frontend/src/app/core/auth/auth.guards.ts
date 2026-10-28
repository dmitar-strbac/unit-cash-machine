import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';

export const sessionGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.refresh().pipe(
    map((user) =>
      user ? (user.role === 'CIT' ? router.parseUrl('/cit') : true) : router.parseUrl('/'),
    ),
    catchError(() => of(router.parseUrl('/'))),
  );
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.refresh().pipe(
    map((user) => (user ? router.parseUrl(user.role === 'CIT' ? '/cit' : '/session') : true)),
    catchError(() => of(true)),
  );
};

export const citGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.refresh().pipe(
    map((user) =>
      user?.role === 'CIT' ? true : router.parseUrl(user ? '/session' : '/login/cit'),
    ),
    catchError(() => of(router.parseUrl('/login/cit'))),
  );
};
