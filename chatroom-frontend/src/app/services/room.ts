import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CreateRoomRequest } from '../dto/CreateRoomRequest';
import { RoomDto } from '../dto/RoomDto';

@Injectable({
  providedIn: 'root'
})
export class RoomService {

  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  createRoom(request: CreateRoomRequest): Observable<RoomDto> {
    return this.http.post<RoomDto>(
      `${this.apiUrl}/api/room`,
      request
    );
  }

  getMyRooms(): Observable<RoomDto[]> {
  return this.http.get<RoomDto[]>(
    `${this.apiUrl}/api/room`
  );
}

getPublicRooms(): Observable<RoomDto[]> {
  return this.http.get<RoomDto[]>(
    `${this.apiUrl}/api/room/public`
  );
}

getRoomById(id: number): Observable<RoomDto> {
  return this.http.get<RoomDto>(
    `${this.apiUrl}/api/room/${id}`
  );
}

joinPublicRoom(roomId: number): Observable<RoomDto> {
  return this.http.post<RoomDto>(
    `${this.apiUrl}/api/room/${roomId}/join`,
    {}
  );
}

leaveRoom(roomId: number): Observable<void> {
  return this.http.delete<void>(
    `${this.apiUrl}/api/room/${roomId}/leave`
  );
}

}