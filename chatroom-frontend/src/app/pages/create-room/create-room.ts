import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CreateRoomRequest } from '../../dto/CreateRoomRequest';
import { RoomService } from '../../services/room';
import { RoomDto } from '../../dto/RoomDto';
import { Subscription, finalize, timeout } from 'rxjs';
import { AgentPickerComponent } from '../../components/agent-picker/agent-picker';
import { AiAgentCatalog } from '../../dto/AiRoomStatus';
import { AiRoomService } from '../../services/ai-room';

@Component({
  selector: 'app-create-room',
  imports: [CommonModule, FormsModule, AgentPickerComponent],
  templateUrl: './create-room.html',
  styleUrl: './create-room.scss',
})
export class CreateRoomComponent implements OnInit, OnDestroy {

  form: CreateRoomRequest = {
    name: '',
    description: '',
    type: 'PUBLIC',
    agentKeys: []
  };

  loading = false;
  successMessage = '';
  errorMessage = '';
  agentCatalog?: AiAgentCatalog;
  agentsLoading = false;
  agentsError = '';
  private readonly subscriptions = new Subscription();

  constructor(
    private roomService: RoomService,
    private router: Router,
    private aiRoomService: AiRoomService
  ) {}

  ngOnInit(): void {
    this.loadAgents();
  }

  loadAgents(): void {
    if (this.agentsLoading) return;
    this.agentsLoading = true;
    this.agentsError = '';
    this.subscriptions.add(this.aiRoomService.getAgents().pipe(
      timeout(12000), finalize(() => { this.agentsLoading = false; })
    ).subscribe({
      next: catalog => { this.agentCatalog = catalog; },
      error: () => {
        this.agentsError = 'Non riusciamo a caricare gli agenti. Puoi creare la room e invitarli in seguito.';
      }
    }));
  }

  createRoom(): void {
    if (this.loading || !this.form.name.trim()) return;
    this.loading = true;
    this.successMessage = '';
    this.errorMessage = '';

    const request = { ...this.form, name: this.form.name.trim(),
      description: this.form.description.trim(), agentKeys: [...(this.form.agentKeys ?? [])] };
    this.subscriptions.add(this.roomService.createRoom(request).subscribe({
      next: (room: RoomDto) => {
        this.loading = false;
        this.successMessage = 'Room creata con successo';

        this.form = {
          name: '',
          description: '',
          type: 'PUBLIC',
          agentKeys: []
        };

        this.router.navigate(['/rooms', room.id, 'chat']);
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage =
          error.error?.message || 'Errore durante la creazione della room';
      }
    }));
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }
}
