export type UserRole = 'CASHIER' | 'MANAGEMENT' | 'MAINTENANCE' | 'CIT';

export interface UserSummary {
  id: string;
  displayName: string;
  role: UserRole;
}

export interface AuthenticatedUser extends UserSummary {
  expiresAt: string;
}

export interface CsrfResponse {
  headerName: string;
  token: string;
}
