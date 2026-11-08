export interface MessageResponse {
  id: number;
  roomId: number;
  senderId: number;
  senderUsername: string;
  content: string;
  sentAt: string;
}