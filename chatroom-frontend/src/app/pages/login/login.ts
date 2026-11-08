import { Component } from '@angular/core';
import { AuthResponse } from '../../dto/AuthResponse';
import { AuthService } from '../../services/auth';
import { LoginRequest } from '../../dto/LoginRequest';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-login',
  imports: [[CommonModule, FormsModule, RouterLink],],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginComponent {

  form: LoginRequest = {
    username: '',
    password: ''
  };

  successMessage = '';
  errorMessage = '';
  loading = false;

  constructor(private authService: AuthService,  private router: Router) {}

  login(): void {
    this.successMessage = '';
    this.errorMessage = '';
    this.loading = true;

    this.authService.login(this.form).subscribe({
      next: (response: AuthResponse) => {
        localStorage.setItem('token', response.token);
        localStorage.setItem('user', JSON.stringify(response.user));

        this.successMessage = 'Login effettuato con successo';
        this.loading = false;
        this.router.navigate(['/home']);
      },
      error: (error) => {
        this.errorMessage =
          error.error?.message || 'Username o password non corretti';

        this.loading = false;
      }
    });
  }
}


