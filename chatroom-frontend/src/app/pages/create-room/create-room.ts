import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CreateRoomRequest } from '../../dto/CreateRoomRequest';
import { RoomService } from '../../services/room';
import { RoomDto } from '../../dto/RoomDto';

@Component({
  selector: 'app-create-room',
  imports: [CommonModule, FormsModule],
  templateUrl: './create-room.html',
  styleUrl: './create-room.scss',
})
export class CreateRoomComponent {

  form: CreateRoomRequest = {
    name: '',
    description: '',
    type: 'PUBLIC'
  };

  loading = false;
  successMessage = '';
  errorMessage = '';

  constructor(
    private roomService: RoomService,
    private router: Router
  ) {}

  createRoom(): void {
    this.loading = true;
    this.successMessage = '';
    this.errorMessage = '';

    this.roomService.createRoom(this.form).subscribe({
      next: (room: RoomDto) => {
        this.loading = false;
        this.successMessage = 'Room creata con successo';

        this.form = {
          name: '',
          description: '',
          type: 'PUBLIC'
        };

        this.router.navigate(['/home']);
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage =
          error.error?.message || 'Errore durante la creazione della room';
      }
    });
  }
}