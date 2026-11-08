import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AiAgentCatalog, AiRoomStatus } from '../dto/AiRoomStatus';

@Injectable({ providedIn: 'root' })
export class AiRoomService {
  constructor(private readonly http: HttpClient) {}

  getAgents(): Observable<AiAgentCatalog> {
    return this.http.get<AiAgentCatalog>(`${environment.apiUrl}/api/ai/agents`);
  }

  setAgents(roomId: number, agentKeys: string[]): Observable<AiRoomStatus> {
    return this.http.put<AiRoomStatus>(`${this.url(roomId)}/agents`, { agentKeys });
  }

  getStatus(roomId: number): Observable<AiRoomStatus> {
    return this.http.get<AiRoomStatus>(this.url(roomId));
  }

  setRunning(roomId: number, running: boolean): Observable<AiRoomStatus> {
    return this.http.post<AiRoomStatus>(this.url(roomId), { running });
  }

  requestTurn(roomId: number): Observable<AiRoomStatus> {
    return this.http.post<AiRoomStatus>(`${this.url(roomId)}/turn`, {});
  }

  private url(roomId: number): string {
    return `${environment.apiUrl}/api/rooms/${roomId}/ai`;
  }
}
