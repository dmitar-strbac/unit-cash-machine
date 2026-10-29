export type ReportType = 'DEVICE_TOTAL' | 'DAY_TOTAL' | 'DAY_TOTAL_USER';

export interface DenominationTotal {
  denomination: number;
  banknoteCount: number;
  totalCents: number;
}

export interface UserTotal {
  userId: string;
  displayName: string;
  banknoteCount: number;
  totalCents: number;
}

export interface ReportView {
  type: ReportType;
  currency: string;
  timeZone: string;
  generatedAt: string;
  periodStart: string;
  periodEnd: string;
  totalCents: number;
  banknoteCount: number;
  denominations: DenominationTotal[];
  users: UserTotal[];
}

export interface ReportOverview {
  deviceTotal: ReportView;
  dayTotal: ReportView;
}

export interface ReceiptView {
  id: string;
  kind: 'DEPOSIT' | ReportType;
  requestedBy: string;
  createdAt: string;
  totalCents: number;
  content: string;
  printStatus: 'NOT_PRINTED' | 'PRINTED' | 'FAILED';
  printError: string | null;
}

export interface ReceiptAccess {
  token: string;
  expiresAt: string;
}

export interface PrintResult {
  receiptId: string;
  requestId: string;
  status: 'PRINTED' | 'FAILED';
  errorCode: string | null;
  replayed: boolean;
}

export type PendingPrint =
  | { kind: 'report'; type: ReportType; requestId: string }
  | { kind: 'reprint'; receiptId: string; requestId: string };
