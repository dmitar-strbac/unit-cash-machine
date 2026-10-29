import { Routes } from '@angular/router';
import { citGuard, guestGuard, managementGuard, sessionGuard } from './core/auth/auth.guards';

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
    path: 'inventory',
    title: 'Unit — Inventory',
    canActivate: [managementGuard],
    loadComponent: () => import('./features/reports/inventory-page').then((m) => m.InventoryPage),
  },
  {
    path: 'inventory/device',
    title: 'Unit — Device Total',
    canActivate: [managementGuard],
    data: { reportType: 'DEVICE_TOTAL' },
    loadComponent: () => import('./features/reports/inventory-page').then((m) => m.InventoryPage),
  },
  {
    path: 'inventory/day',
    title: 'Unit — Day Total',
    canActivate: [managementGuard],
    data: { reportType: 'DAY_TOTAL' },
    loadComponent: () => import('./features/reports/inventory-page').then((m) => m.InventoryPage),
  },
  {
    path: 'receipts',
    title: 'Unit — Virtual printer',
    canActivate: [managementGuard],
    loadComponent: () => import('./features/printing/receipts-page').then((m) => m.ReceiptsPage),
  },
  {
    path: 'deposit-receipt',
    title: 'Unit — Deposit receipt',
    loadComponent: () =>
      import('./features/printing/deposit-receipt-page').then((m) => m.DepositReceiptPage),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
