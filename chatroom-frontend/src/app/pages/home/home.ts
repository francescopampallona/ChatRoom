import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { RoomService } from '../../services/room';
import { RoomDto } from '../../dto/RoomDto';
import { CommonModule } from '@angular/common';
import { UserDto } from '../../dto/UserDto';

@Component({
  selector: 'app-home',
  templateUrl: './home.html',
  imports: [CommonModule],
  styleUrl: './home.scss'
})
export class HomeComponent {

  username = '';

  constructor(private router: Router, private roomService: RoomService) {}

  myRooms: RoomDto[] = [];
  publicRooms: RoomDto[] = [];

  loading = false;
  errorMessage = '';

  currentUser!: UserDto;


  ngOnInit(): void {
    
    const userString = localStorage.getItem('user');


    if (userString){
      
      this.currentUser = JSON.parse(userString);


      this.username = this.currentUser.username;
    }
    this.loadRooms();
  }

  goToCreateRoom(): void {
    this.router.navigate(['/rooms/create']);
  }

  loadRooms(): void {
  this.loading = true;

  this.roomService.getMyRooms().subscribe({
    next: rooms => {
      this.myRooms = this.aiRoomsFirst(rooms);
      this.loadPublicRooms();
    },
    error: () => {
      this.errorMessage = 'Errore nel caricamento delle room';
      this.loading = false;
    }
  });
}

loadPublicRooms(): void {
  this.roomService.getPublicRooms().subscribe({
    next: rooms => {
      this.publicRooms = this.aiRoomsFirst(rooms);
      this.loading = false;
    },
    error: () => {
      this.errorMessage = 'Errore nel caricamento delle room pubbliche';
      this.loading = false;
    }
  });
}

joinRoom(roomId: number, openAfterJoin = false): void {
  if (this.loading) return;
  this.loading = true;
  this.errorMessage = '';

  this.roomService.joinPublicRoom(roomId).subscribe({
    next: () => {
      if (openAfterJoin) {
        this.loading = false;
        this.openChat(roomId);
      } else {
        this.loadRooms();
      }
    },
    error: (error) => {
      this.loading = false;
      this.errorMessage =
        error.error?.message || 'Errore durante l’ingresso nella room';
    }
  });
}

isMyRoom(roomId: number): boolean {
  return this.myRooms.some(room => room.id === roomId);
}

isOwner(room: RoomDto): boolean {
  return room.ownerId === this.currentUser?.id;
}

leaveRoom(roomId: number): void {
  this.loading = true;
  this.errorMessage = '';

  this.roomService.leaveRoom(roomId).subscribe({
    next: () => {
      this.loadRooms();
    },
    error: (error) => {
      this.loading = false;
      this.errorMessage =
        error.error?.message || 'Errore durante l’uscita dalla room';
    }
  });
}

goToInvite(roomId: number): void {
  this.router.navigate(['/rooms', roomId, 'invite']);
}
goToInvites(): void {
  this.router.navigate(['/invites']);
}

openChat(roomId: number): void {
  this.router.navigate([
    '/rooms',
    roomId,
    'chat'
  ]);
}

private aiRoomsFirst(rooms: RoomDto[]): RoomDto[] {
  return [...rooms].sort((left, right) => Number(!!right.aiRoom) - Number(!!left.aiRoom));
}

}
