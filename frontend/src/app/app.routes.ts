import { Routes } from '@angular/router';
import { citGuard, guestGuard, sessionGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Unit — Home',
    loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
  },
  {
    path: 'settings',
    title: 'Unit — Settings',
    loadComponent: () => import('./features/settings/settings-page').then((m) => m.SettingsPage),
  },
  {
    path: 'login/cashier',
    title: 'Unit — Cashier login',
    canActivate: [guestGuard],
    data: { role: 'CASHIER' },
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'login/management',
    title: 'Unit — Management login',
    canActivate: [guestGuard],
    data: { role: 'MANAGEMENT' },
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'login/maintenance',
    title: 'Unit — Maintenance login',
    canActivate: [guestGuard],
    data: { role: 'MAINTENANCE' },
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'login/cit',
    title: 'Unit — CIT login',
    canActivate: [guestGuard],
    data: { role: 'CIT' },
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'cit',
    title: 'Unit — CIT collection',
    canActivate: [citGuard],
    loadComponent: () =>
      import('./features/collection/collection-page').then((m) => m.CollectionPage),
  },
  {
    path: 'session',
    title: 'Unit — Session',
    canActivate: [sessionGuard],
    loadComponent: () => import('./features/auth/session-page').then((m) => m.SessionPage),
  },
  {
    path: 'diagnostics',
    title: 'Unit — Diagnostics',
    loadComponent: () =>
      import('./features/diagnostics/diagnostics-page').then((m) => m.DiagnosticsPage),
  },
  {
    path: 'info',
    title: 'Unit — Information',
    loadComponent: () => import('./features/info/info-page').then((m) => m.InfoPage),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
