export interface MessageResponse {
  id: number;
  roomId: number;
  senderId: number;
  senderUsername: string;
  senderIsAgent: boolean;
  content: string;
  sentAt: string;
}
