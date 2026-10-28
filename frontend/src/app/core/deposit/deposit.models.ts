export interface DepositView {
  id: string;
  status: 'ACTIVE' | 'COMPLETED' | 'EXPIRED' | 'INTERRUPTED';
  currency: 'EUR';
  totalCents: number;
  banknoteCount: number;
  startedAt: string;
  expiresAt: string;
  finishedAt: string | null;
  acceptingEnabled: boolean;
}

export interface BanknoteRequest {
  operationId: string;
  currency: string;
  denomination: number;
}

export interface BanknoteResult {
  operationId: string;
  outcome: 'ACCEPTED' | 'CURRENCY_REJECTED' | 'DENOMINATION_REJECTED';
  replayed: boolean;
  deposit: DepositView;
}

export interface PendingBanknote {
  depositId: string;
  request: BanknoteRequest;
}
