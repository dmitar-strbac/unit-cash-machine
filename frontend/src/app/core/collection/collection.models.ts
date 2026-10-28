export type CollectionStep =
  | 'OPEN_DOOR'
  | 'REMOVE_CASSETTE'
  | 'INSERT_CASSETTE'
  | 'CLOSE_DOOR'
  | 'SECURING'
  | 'INCOMPLETE'
  | 'COMPLETED';

export type PhysicalAction =
  'OPEN_DOOR' | 'CLOSE_DOOR' | 'LOCK_DOOR' | 'REMOVE_CASSETTE' | 'INSERT_CASSETTE';

export interface CollectionView {
  id: string;
  step: CollectionStep;
  doorClosed: boolean;
  doorLocked: boolean;
  removalConfirmed: boolean;
  insertionConfirmed: boolean;
  startedAt: string;
  securingDeadline: string | null;
  finishedAt: string | null;
  collectedTotalCents: number | null;
  errorCode: string | null;
}
