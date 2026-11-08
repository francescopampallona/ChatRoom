import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, Observable, Subscription, catchError, exhaustMap, finalize, tap, timeout, timer } from 'rxjs';
import { AiAgentCatalog, AiRoomStatus } from '../../dto/AiRoomStatus';
import { AgentPickerComponent } from '../../components/agent-picker/agent-picker';
import { MessageResponse } from '../../dto/MessageResponse';
import { RoomDto } from '../../dto/RoomDto';
import { AiRoomService } from '../../services/ai-room';
import { ChatService } from '../../services/chat';
import { MessageService } from '../../services/message';
import { RoomService } from '../../services/room';

@Component({
  selector: 'app-room-chat',
  imports: [CommonModule, FormsModule, AgentPickerComponent],
  templateUrl: './room-chat.html',
  styleUrl: './room-chat.scss',
})
export class RoomChatComponent implements OnInit, OnDestroy {
  roomId!: number;
  room?: RoomDto;
  messages: MessageResponse[] = [];
  newMessage = '';
  connected = false;
  sending = false;
  loadingMessages = true;
  errorMessage = '';

  aiStatus?: AiRoomStatus;
  aiBusy = false;
  aiStatusError = '';
  aiActionError = '';
  agentCatalog?: AiAgentCatalog;
  agentCatalogLoading = false;
  agentCatalogError = '';
  selectedAgentKeys: string[] = [];
  agentSelectionDirty = false;
  agentEditorOpen = false;

  @ViewChild('messagesContainer')
  private messagesContainer?: ElementRef<HTMLDivElement>;

  private readonly subscriptions = new Subscription();
  private historyRequest?: Subscription;
  private scrollTimer?: ReturnType<typeof setTimeout>;
  private aiUpdateVersion = 0;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly chatService: ChatService,
    private readonly roomService: RoomService,
    private readonly messageService: MessageService,
    private readonly aiRoomService: AiRoomService
  ) {}

  ngOnInit(): void {
    this.roomId = Number(this.route.snapshot.paramMap.get('roomId'));
    if (!Number.isInteger(this.roomId) || this.roomId <= 0) {
      void this.router.navigate(['/home']);
      return;
    }

    this.subscriptions.add(this.roomService.getRoomById(this.roomId).subscribe({
      next: room => {
        this.room = room;
        this.openConversation();
        this.pollAiStatus();
      },
      error: () => {
        this.loadingMessages = false;
        this.errorMessage = 'Impossibile aprire la room. Verifica di essere ancora un membro.';
      }
    }));
  }

  get aiStatusLabel(): string {
    if (this.aiStatusError) return 'Stato non disponibile';
    if (!this.aiStatus) return 'Preparazione degli agenti…';
    if (!this.aiStatus.agents.length) return 'Nessun agente invitato';
    if (!this.aiStatus.enabled || !this.aiStatus.ready) return 'Agenti non disponibili';
    if (this.aiStatus.generating) return 'Un agente sta scrivendo…';
    if (this.aiStatus.error) return 'Risposta interrotta';
    if (!this.aiStatus.running) return 'In pausa';
    return this.aiStatus.remainingTurns > 0 ? 'Conversazione in corso' : 'In ascolto';
  }

  get aiStatusHint(): string {
    if (this.aiStatusError) return 'Riproviamo automaticamente tra pochi secondi. Puoi continuare a chattare.';
    if (!this.aiStatus) return 'Stiamo verificando se gli agenti sono pronti a conversare.';
    if (!this.aiStatus.agents.length) return 'Scegli chi invitare: gli agenti seguiranno il tema della room e leggeranno solo questa conversazione.';
    if (!this.aiStatus.enabled || !this.aiStatus.ready) {
      return 'Gli agenti non sono ancora pronti. Puoi continuare a scrivere mentre attendi.';
    }
    if (this.aiStatus.generating && !this.aiStatus.running) {
      return 'È in corso una risposta. La conversazione automatica resta in pausa.';
    }
    if (!this.aiStatus.running) return this.aiStatus.agents.length === 1
      ? 'Avvia la conversazione con l’agente oppure chiedi una sola risposta.'
      : 'Avvia il dialogo tra gli agenti oppure chiedi una sola risposta.';
    if (this.aiStatus.remainingTurns === 0) {
      return 'Gli agenti attendono un tuo messaggio per riprendere la conversazione.';
    }
    return this.aiStatus.agents.length === 1
      ? 'L’agente interviene sul tema della room. Scrivigli quando vuoi; dopo alcuni interventi attende un tuo messaggio.'
      : 'Gli agenti si rispondono a turno. Intervieni quando vuoi; dopo alcuni scambi attendono un tuo messaggio.';
  }

  get canAskAgent(): boolean {
    return !!this.aiStatus?.agents.length && !!this.aiStatus.enabled && this.aiStatus.ready && !this.aiStatus.generating
      && !this.aiBusy && !this.aiStatusError;
  }

  get showAiPanel(): boolean {
    return !!this.room?.aiRoom || !!this.aiStatus?.canManageAgents || !!this.aiStatus?.agents.length;
  }

  get aiPanelTitle(): string {
    if (this.room?.aiRoom) return 'Tre voci, una conversazione';
    if (!this.aiStatus?.agents.length) return 'Invita agenti nella tua chat';
    return this.aiStatus.agents.length === 1 ? 'Un agente, il tuo tema' : 'Il tuo tema, più punti di vista';
  }

  selectAgents(keys: string[]): void {
    if (this.aiBusy || !this.aiStatus?.canManageAgents || !this.agentCatalog?.enabled) return;
    this.selectedAgentKeys = keys;
    const current = this.aiStatus.agents.map(agent => agent.key);
    this.agentSelectionDirty = keys.length !== current.length || keys.some(key => !current.includes(key));
  }

  saveAgents(): void {
    if (this.aiBusy || !this.aiStatus?.canManageAgents || !this.agentCatalog?.enabled || !this.agentSelectionDirty) return;
    this.runAiAction(this.aiRoomService.setAgents(this.roomId, [...this.selectedAgentKeys]), true);
  }

  cancelAgentSelection(): void {
    if (this.aiBusy) return;
    this.selectedAgentKeys = this.aiStatus?.agents.map(agent => agent.key) ?? [];
    this.agentSelectionDirty = false;
    this.agentEditorOpen = false;
  }

  loadAgentCatalog(): void {
    if (this.agentCatalogLoading) return;
    this.agentCatalogLoading = true;
    this.agentCatalogError = '';
    this.subscriptions.add(this.aiRoomService.getAgents().pipe(
      timeout(12000), finalize(() => { this.agentCatalogLoading = false; })
    ).subscribe({
      next: catalog => { this.agentCatalog = catalog; },
      error: () => { this.agentCatalogError = 'Non riusciamo a caricare gli agenti disponibili. Riprova tra poco.'; }
    }));
  }

  setAiRunning(running: boolean): void {
    if (this.aiBusy || (running && !this.canAskAgent)) return;
    this.runAiAction(this.aiRoomService.setRunning(this.roomId, running));
  }

  requestAiTurn(): void {
    if (!this.canAskAgent) return;
    this.runAiAction(this.aiRoomService.requestTurn(this.roomId));
  }

  senderName(message: MessageResponse): string {
    if (!message.senderIsAgent) return message.senderUsername;
    return this.aiStatus?.agents.find(agent => agent.id === message.senderId)?.displayName
      ?? message.senderUsername;
  }

  trackMessage(_index: number, message: MessageResponse): number {
    return message.id;
  }

  sendMessage(): void {
    const content = this.newMessage.trim();
    if (!content || !this.connected || this.sending) return;
    this.errorMessage = '';
    this.sending = true;
    try {
      this.chatService.sendMessage(this.roomId, content);
      this.newMessage = '';
    } catch {
      this.errorMessage = 'Impossibile inviare il messaggio. Riprova tra poco.';
    } finally {
      this.sending = false;
    }
  }

  goHome(): void {
    void this.router.navigate(['/home']);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.historyRequest?.unsubscribe();
    clearTimeout(this.scrollTimer);
    if (Number.isInteger(this.roomId) && this.roomId > 0) {
      this.chatService.unsubscribeFromRoom(this.roomId);
    }
  }

  private openConversation(): void {
    this.subscriptions.add(this.chatService.messages$.subscribe(message => {
      if (message.roomId !== this.roomId) return;
      const shouldScroll = this.isNearBottom();
      this.mergeMessages([message]);
      if (shouldScroll) this.queueScroll();
    }));

    this.loadMessages();
    this.subscriptions.add(this.chatService.connected$.subscribe(connected => {
      this.connected = connected;
      if (connected) {
        this.chatService.subscribeToRoom(this.roomId);
        // Recover replies sent while the browser was disconnected.
        this.loadMessages();
      }
    }));

    try {
      this.chatService.connect();
    } catch {
      this.errorMessage = 'Impossibile collegarsi alla chat. Accedi nuovamente e riprova.';
    }
  }

  private pollAiStatus(): void {
    this.subscriptions.add(timer(0, 4000).pipe(
      exhaustMap(() => {
        if (this.aiBusy) return EMPTY;
        const version = this.aiUpdateVersion;
        return this.aiRoomService.getStatus(this.roomId).pipe(
          timeout(12000),
          tap(status => {
            if (version !== this.aiUpdateVersion) return;
            this.applyAiStatus(status);
          }),
          catchError(() => {
            if (version === this.aiUpdateVersion) {
              this.aiStatusError = 'Non riusciamo ad aggiornare lo stato degli agenti.';
            }
            return EMPTY;
          })
        );
      })
    ).subscribe());
  }

  private applyAiStatus(status: AiRoomStatus): void {
    this.aiStatus = status;
    this.aiStatusError = '';
    if (!this.agentSelectionDirty) this.selectedAgentKeys = status.agents.map(agent => agent.key);
    if (status.canManageAgents && !this.agentCatalog && !this.agentCatalogLoading && !this.agentCatalogError) {
      this.loadAgentCatalog();
    }
  }

  private runAiAction(request: Observable<AiRoomStatus>, selectionChange = false): void {
    this.aiBusy = true;
    this.aiActionError = '';
    // A poll started before a command must not overwrite the command's result.
    this.aiUpdateVersion++;
    this.subscriptions.add(request.pipe(
      timeout(15000),
      finalize(() => { this.aiBusy = false; })
    ).subscribe({
      next: status => {
        if (selectionChange) {
          this.agentSelectionDirty = false;
          this.agentEditorOpen = false;
        }
        this.applyAiStatus(status);
      },
      error: error => {
        this.aiActionError = error.status === 403
          ? selectionChange ? 'Solo il proprietario della room può scegliere gli agenti.' : 'Solo i membri della room possono gestire la conversazione IA.'
          : error.status === 409
            ? 'Un agente sta già preparando una risposta. Attendi qualche istante.'
            : 'Non è stato possibile aggiornare gli agenti. Attendi qualche istante e riprova.';
      }
    }));
  }

  private loadMessages(): void {
    this.historyRequest?.unsubscribe();
    this.loadingMessages = true;
    this.historyRequest = this.messageService.getMessages(this.roomId, 0, 20).subscribe({
      next: response => {
        const shouldScroll = this.isNearBottom();
        this.mergeMessages(response.content);
        this.loadingMessages = false;
        if (shouldScroll) this.queueScroll();
      },
      error: () => {
        this.loadingMessages = false;
        this.errorMessage = 'Errore nel caricamento dei messaggi precedenti.';
      }
    });
  }

  private mergeMessages(incoming: MessageResponse[]): void {
    const byId = new Map(this.messages.map(message => [message.id, message]));
    incoming.forEach(message => byId.set(message.id, message));
    this.messages = [...byId.values()].sort((left, right) =>
      (Date.parse(left.sentAt) - Date.parse(right.sentAt)) || left.id - right.id
    );
  }

  private queueScroll(): void {
    clearTimeout(this.scrollTimer);
    this.scrollTimer = setTimeout(() => {
      const element = this.messagesContainer?.nativeElement;
      element?.scrollTo({ top: element.scrollHeight, behavior: 'smooth' });
    });
  }

  private isNearBottom(): boolean {
    const element = this.messagesContainer?.nativeElement;
    return !element || element.scrollHeight - element.scrollTop - element.clientHeight < 100;
  }
}
