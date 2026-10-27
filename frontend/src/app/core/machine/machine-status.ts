export interface HardwareSnapshot {
  acceptor: 'READY' | 'ACCEPTING' | 'JAMMED' | 'OFFLINE' | 'UNKNOWN';
  cashbox: 'PRESENT' | 'FULL' | 'ABSENT' | 'UNKNOWN';
  door: 'CLOSED' | 'OPEN' | 'UNKNOWN';
  lock: 'LOCKED' | 'UNLOCKED' | 'FAULT' | 'UNKNOWN';
  extensionBoard: 'ONLINE' | 'OFFLINE' | 'UNKNOWN';
  printer: 'READY' | 'OUT_OF_PAPER' | 'OFFLINE' | 'FAULT' | 'UNKNOWN';
}

export interface MachineAlert {
  code: string;
  source: 'ACCEPTOR' | 'CASHBOX' | 'DOOR' | 'LOCK' | 'EXTENSION_BOARD' | 'PRINTER';
  severity: 'WARNING' | 'ERROR';
  message: string;
  blocksDeposit: boolean;
}

export interface MachineStatus {
  machineCode: string;
  displayName: string;
  location: string;
  currency: string;
  timeZone: string;
  simulated: boolean;
  secure: boolean;
  depositHardwareReady: boolean;
  printingAvailable: boolean;
  hardware: HardwareSnapshot;
  indicator: 'GREEN' | 'AMBER' | 'RED';
  alertCount: number;
  alerts: MachineAlert[];
}
