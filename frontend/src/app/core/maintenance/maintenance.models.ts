export type MachineRunState =
  'RUNNING' | 'RESETTING' | 'REBOOTING' | 'SHUT_DOWN' | 'APPLICATION_STOPPED';

export type MaintenanceAction =
  'RESET' | 'REBOOT' | 'SHUT_DOWN' | 'EXIT_APPLICATION' | 'POWER_ON' | 'START_APPLICATION';

export interface MachineRuntimeView {
  state: MachineRunState;
  generation: number;
  commandId: string | null;
  action: MaintenanceAction | null;
  completesAt: string | null;
  notice: string | null;
}

export interface MaintenanceCommand {
  id: string;
  action: MaintenanceAction;
  requestedBy: string | null;
  status: 'PENDING' | 'COMPLETED';
  requestedAt: string;
  completesAt: string;
  finishedAt: string | null;
}

export interface MachineComponent {
  id: string;
  description: string;
  state: string;
  simulated: boolean;
  derived: boolean;
  metadata: Record<string, string>;
  resetCount: number;
  lastResetAt: string | null;
}
