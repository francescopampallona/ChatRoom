import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { InviteService } from '../../services/invite';
import { InviteResponse } from '../../dto/InviteResponse';
import { Router } from '@angular/router';

@Component({
  selector: 'app-my-invites',
  imports: [CommonModule],
  templateUrl: './my-invites.html',
  styleUrl: './my-invites.scss',
})
export class MyInvitesComponent {

  invites: InviteResponse[] = [];

  loading = false;
  errorMessage = '';
  successMessage = '';

  constructor(private inviteService: InviteService,private router: Router) {}

  ngOnInit(): void {
    this.loadInvites();
  }

  loadInvites(): void {
    this.loading = true;
    this.errorMessage = '';

    this.inviteService.getMyInvites().subscribe({
      next: (invites) => {
        this.invites = invites;
        this.loading = false;
      },
      error: (error) => {
        this.errorMessage =
          error.error?.message || 'Errore durante il caricamento degli inviti';
        this.loading = false;
      }
    });
  }

  acceptInvite(inviteId: number): void {
    this.successMessage = '';
    this.errorMessage = '';

    this.inviteService.acceptInvite(inviteId).subscribe({
      next: (response) => {
        this.successMessage = `Hai accettato l'invito alla room ${response.roomName}`;
        this.invites = this.invites.filter(invite => invite.id !== inviteId);
      },
      error: (error) => {
        if (typeof error.error === 'string') {
          this.errorMessage = error.error;
        } else {
          this.errorMessage =
            error.error?.message || 'Errore durante l’accettazione dell’invito';
        }
      }
    });
  }

  declineInvite(inviteId: number): void {
  this.successMessage = '';
  this.errorMessage = '';

  this.inviteService.declineInvite(inviteId).subscribe({
    next: () => {
      this.successMessage = 'Invito rifiutato';
      this.invites = this.invites.filter(invite => invite.id !== inviteId);
    },
    error: (error) => {
      if (typeof error.error === 'string') {
        this.errorMessage = error.error;
      } else {
        this.errorMessage =
          error.error?.message || 'Errore durante il rifiuto dell’invito';
      }
    }
  });
}

  goHome(): void {
  this.router.navigate(['/home']);
}
}