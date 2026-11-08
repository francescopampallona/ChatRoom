import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { MessageResponse } from '../dto/MessageResponse';
import { environment } from '../../environments/environment';
import { PageResponse } from '../dto/PageResponse';

@Injectable({
  providedIn: 'root',
})
export class MessageService {
 private readonly apiUrl = environment.apiUrl;

 constructor(private http: HttpClient) {}

getMessages(
  roomId: number,
  page = 0,
  size = 20
): Observable<PageResponse<MessageResponse>> {
  return this.http.get<PageResponse<MessageResponse>>(
    `${environment.apiUrl}/api/rooms/${roomId}/messages`,
    {
      params: {
        page: page.toString(),
        size: size.toString()
      }
    }
  );
}
  
}
