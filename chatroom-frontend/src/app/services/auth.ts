import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { RegisterRequest } from '../dto/RegisterRequest';
import { Observable } from 'rxjs';
import { AuthResponse } from '../dto/AuthResponse';
import { environment } from '../../environments/environment';
import { LoginRequest } from '../dto/LoginRequest';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  
   private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/api/auth/register`, request);
  }

  login(request: LoginRequest): Observable<AuthResponse> {
  return this.http.post<AuthResponse>(
    `${this.apiUrl}/api/auth/login`,
    request
  );
}
  
}
