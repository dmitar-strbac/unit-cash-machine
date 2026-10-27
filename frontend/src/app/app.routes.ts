import { Routes } from '@angular/router';

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
