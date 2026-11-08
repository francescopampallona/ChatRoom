export interface AiParticipant {
  key: string;
  id: number;
  username: string;
  displayName: string;
  description: string;
}

export interface AiAgentCatalog {
  enabled: boolean;
  agents: AiParticipant[];
}

export interface AiRoomStatus {
  enabled: boolean;
  ready: boolean;
  running: boolean;
  generating: boolean;
  remainingTurns: number;
  model: string;
  error: string | null;
  agents: AiParticipant[];
  canManageAgents: boolean;
}
