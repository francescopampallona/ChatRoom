import { Injectable } from '@angular/core';
import { environment } from '../../environments/environment';
import { HttpClient } from '@angular/common/http';
import { InviteUserRequest } from '../dto/InviteUserRequest';
import { InviteResponse } from '../dto/InviteResponse';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class InviteService {
  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  inviteUser(roomId: number, request: InviteUserRequest): Observable<InviteResponse> {
    return this.http.post<InviteResponse>(
      `${this.apiUrl}/api/invite/${roomId}/invites`,
      request
    );
  }

getMyInvites(): Observable<InviteResponse[]> {
  return this.http.get<InviteResponse[]>(
    `${this.apiUrl}/api/invite/my`
  );
}

acceptInvite(inviteId: number): Observable<InviteResponse> {
  return this.http.post<InviteResponse>(
    `${this.apiUrl}/api/invite/${inviteId}/accept`,
    {}
  );
}
declineInvite(inviteId: number): Observable<InviteResponse> {
  return this.http.post<InviteResponse>(
    `${this.apiUrl}/api/invite/${inviteId}/decline`,
    {}
  );
}
}
