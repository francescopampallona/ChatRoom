import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ActivatedRoute,
  Router
} from '@angular/router';
import { Subscription } from 'rxjs';
import { ChatService } from '../../services/chat';
import { MessageResponse } from '../../dto/MessageResponse';
import { RoomDto } from '../../dto/RoomDto';
import { RoomService } from '../../services/room';
import { MessageService } from '../../services/message';

@Component({
  selector: 'app-room-chat',
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './room-chat.html',
  styleUrl: './room-chat.scss',
})
export class RoomChatComponent implements OnInit, OnDestroy {

  roomId!: number;

  messages: MessageResponse[] = [];
  newMessage = '';

  connected = false;
  sending = false;
  errorMessage = '';

  room?: RoomDto;

  @ViewChild('messagesContainer')
  private messagesContainer?: ElementRef<HTMLDivElement>;

  private readonly subscriptions =
    new Subscription();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly chatService: ChatService,
    private readonly roomService: RoomService,
    private readonly messageService: MessageService
  ) {}

  ngOnInit(): void {
    this.roomId = Number(
      this.route.snapshot.paramMap.get('roomId')
    );

    this.loadMessages();

    if (!Number.isFinite(this.roomId)) {
      this.router.navigate(['/home']);
      return;
    }

    this.subscriptions.add(
      this.chatService.connected$.subscribe(connected => {
        this.connected = connected;

        if (connected) {
          this.chatService.subscribeToRoom(this.roomId);
        }
      })
    );

    this.subscriptions.add(
      this.chatService.messages$.subscribe(message => {
          if (message.roomId !== this.roomId) {
    return;
  }

  const shouldScroll = this.isNearBottom();

  const exists = this.messages.some(
    m => m.id === message.id
);

if (!exists) {

    this.messages.push(message);

}

  if (shouldScroll) {
    setTimeout(() => this.scrollToBottom());
  }
      })
    );

  this.roomService.getRoomById(this.roomId).subscribe({next: room => {
    this.room = room;
  },error: () => {
    this.errorMessage = 'Impossibile caricare la room';}
  });

    this.chatService.connect();
  }

  sendMessage(): void {
    const content = this.newMessage.trim();

    if (!content || !this.connected) {
      return;
    }

    this.errorMessage = '';
    this.sending = true;

    try {
      this.chatService.sendMessage(
        this.roomId,
        content
      );

      this.newMessage = '';
    } catch (error) {
      console.error(error);
      this.errorMessage =
        'Impossibile inviare il messaggio';
    } finally {
      this.sending = false;
    }
  }

  goHome(): void {
    this.router.navigate(['/home']);
  }

  ngOnDestroy(): void {
    this.chatService.unsubscribeFromRoom(
      this.roomId
    );

    this.subscriptions.unsubscribe();
  }

  private scrollToBottom(): void {

  if (!this.messagesContainer) {
    return;
  }

  const element = this.messagesContainer.nativeElement;

  element.scrollTo({
    top: element.scrollHeight,
    behavior: 'smooth'
  });

}

private isNearBottom(): boolean {

  if (!this.messagesContainer) {
    return true;
  }

  const element = this.messagesContainer.nativeElement;

  return element.scrollHeight - element.scrollTop - element.clientHeight < 100;
}

private loadMessages(): void {

    this.messageService
        .getMessages(this.roomId, 0, 20)
        .subscribe({

            next: response => {

                this.messages =
                    response.content.reverse();

                this.chatService.connect();

                setTimeout(() => this.scrollToBottom());

            },

            error: () => {

                this.errorMessage =
                    'Errore nel caricamento dei messaggi';

            }

        });

}
}