export interface CreateRoomRequest {
  name: string;
  description: string;
  type: 'PUBLIC' | 'PRIVATE';
}