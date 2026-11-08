import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { InviteService } from '../../services/invite';
import { InviteUserRequest } from '../../dto/InviteUserRequest';
import { InviteResponse } from '../../dto/InviteResponse';

@Component({
  selector: 'app-invite-user',
  imports: [CommonModule, FormsModule],
  templateUrl: './invite-user.html',
  styleUrl: './invite-user.scss',
})
export class InviteUserComponent {

  roomId!: number;

  form: InviteUserRequest = {
    username: ''
  };

  loading = false;
  successMessage = '';
  errorMessage = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private inviteService: InviteService
  ) {}

  ngOnInit(): void {
    this.roomId = Number(this.route.snapshot.paramMap.get('roomId'));
  }

  inviteUser(): void {
    this.loading = true;
    this.successMessage = '';
    this.errorMessage = '';

    this.inviteService.inviteUser(this.roomId, this.form).subscribe({
      next: (response: InviteResponse) => {
        this.loading = false;
        this.successMessage = `Invito inviato a ${response.invitedUsername}`;

        this.form = {
          username: ''
        };
      },
       error: (error) => {
  this.loading = false;

  if (typeof error.error === 'string') {
    this.errorMessage = error.error;
  } else if (error.error?.message) {
    this.errorMessage = error.error.message;
  } else {
    this.errorMessage = 'Errore durante l’invito dell’utente';
  }
}
});
  }

  goBack(): void {
    this.router.navigate(['/home']);
  }
}