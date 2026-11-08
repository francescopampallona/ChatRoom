export interface RoomDto {
  id: number;
  name: string;
  description: string;
  type: 'PUBLIC' | 'PRIVATE';
  ownerId: number;
  ownerUsername: string;
  createdAt: string;
}