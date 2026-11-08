export interface InviteResponse {
  id: number;

  roomId: number;
  roomName: string;

  invitedUserId: number;
  invitedUsername: string;

  invitedByUserId: number;
  invitedByUsername: string;

  status: 'PENDING' | 'ACCEPTED' | 'DECLINED';

  createdAt: string;
}