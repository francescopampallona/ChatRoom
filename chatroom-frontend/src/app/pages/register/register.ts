import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth';
import { RegisterRequest } from '../../dto/RegisterRequest';
import { AuthResponse } from '../../dto/AuthResponse';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class RegisterComponent {

  form: RegisterRequest = {
    username: '',
    email: '',
    password: ''
  };

  successMessage = '';
  errorMessage = '';
  loading = false;

  constructor(private authService: AuthService,  private router: Router) {}

  register(): void {

    this.successMessage = '';
    this.errorMessage = '';
    this.loading = true;

    this.authService.register(this.form).subscribe({

      next: (response: AuthResponse) => {

        console.log('Registrazione completata', response);

        this.successMessage = 'Registrazione completata con successo';

        this.loading = false;

        this.form = {
          username: '',
          email: '',
          password: ''
        };
        
        localStorage.setItem('token', response.token);
        localStorage.setItem('user',JSON.stringify(response.user));
        this.router.navigate(['/home']);
      },

      error: (error) => {

        console.error(error);

        if (error.error) {

          if (typeof error.error === 'string') {
            this.errorMessage = error.error;
          } else {

            const firstKey = Object.keys(error.error)[0];

            if (firstKey) {
              this.errorMessage = error.error[firstKey];
            } else {
              this.errorMessage = 'Errore durante la registrazione';
            }
          }

        } else {
          this.errorMessage = 'Server non raggiungibile';
        }

        this.loading = false;
      }
    });
  }
}