export interface RoomDto {
  id: number;
  name: string;
  description: string;
  type: 'PUBLIC' | 'PRIVATE';
  aiRoom: boolean;
  ownerId: number;
  ownerUsername: string;
  createdAt: string;
}
