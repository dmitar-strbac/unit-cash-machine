import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';

export const sessionGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.refresh().pipe(
    map((user) => (user ? true : router.parseUrl('/'))),
    catchError(() => of(router.parseUrl('/'))),
  );
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.refresh().pipe(
    map((user) => (user ? router.parseUrl('/session') : true)),
    catchError(() => of(true)),
  );
};
