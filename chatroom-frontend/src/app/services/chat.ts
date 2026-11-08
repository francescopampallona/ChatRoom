import { Injectable } from '@angular/core';
import {
  Client,
  IMessage,
  StompSubscription
} from '@stomp/stompjs';
import {
  BehaviorSubject,
  Observable,
  Subject
} from 'rxjs';
import { environment } from '../../environments/environment';
import { MessageRequest } from '../dto/MessageRequest';
import { MessageResponse } from '../dto/MessageResponse';

@Injectable({
  providedIn: 'root',
})
export class ChatService {

  private client?: Client;

  private readonly roomSubscriptions =
    new Map<number, StompSubscription>();

  private readonly messagesSubject =
    new Subject<MessageResponse>();

  private readonly connectedSubject =
    new BehaviorSubject<boolean>(false);

  readonly messages$: Observable<MessageResponse> =
    this.messagesSubject.asObservable();

  readonly connected$: Observable<boolean> =
    this.connectedSubject.asObservable();

  connect(): void {
    if (this.client?.active) {
      return;
    }

    const token = localStorage.getItem('token');

    if (!token) {
      throw new Error('Token JWT non disponibile');
    }

    this.client = new Client({
      brokerURL: this.getWebSocketUrl(),

      connectHeaders: {
        Authorization: `Bearer ${token}`
      },

      reconnectDelay: 5000,

      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        // Subscriptions belong to a STOMP connection and must be recreated on reconnect.
        this.roomSubscriptions.clear();
        this.connectedSubject.next(true);
      },

      onDisconnect: () => {
        this.connectedSubject.next(false);
      },

      onWebSocketClose: () => {
        this.roomSubscriptions.clear();
        this.connectedSubject.next(false);
      },

      onWebSocketError: error => {
        console.error('Errore WebSocket', error);
        this.connectedSubject.next(false);
      },

      onStompError: frame => {
        console.error(
          'Errore STOMP:',
          frame.headers['message'],
          frame.body
        );
      }
    });

    this.client.activate();
  }

  subscribeToRoom(roomId: number): void {
    if (!this.client?.connected) {
      throw new Error('WebSocket non connesso');
    }

    if (this.roomSubscriptions.has(roomId)) {
      return;
    }

    const subscription = this.client.subscribe(
      `/topic/rooms/${roomId}`,
      (message: IMessage) => {
        const response =
          JSON.parse(message.body) as MessageResponse;

        this.messagesSubject.next(response);
      }
    );

    this.roomSubscriptions.set(roomId, subscription);
  }

  sendMessage(roomId: number, content: string): void {
    if (!this.client?.connected) {
      throw new Error('WebSocket non connesso');
    }

    const normalizedContent = content.trim();

    if (!normalizedContent) {
      return;
    }

    const request: MessageRequest = {
      content: normalizedContent
    };

    this.client.publish({
      destination: `/app/rooms/${roomId}/messages`,
      body: JSON.stringify(request),
      headers: {
        'content-type': 'application/json'
      }
    });
  }

  unsubscribeFromRoom(roomId: number): void {
    const subscription =
      this.roomSubscriptions.get(roomId);

    subscription?.unsubscribe();

    this.roomSubscriptions.delete(roomId);
  }

  disconnect(): void {
    this.roomSubscriptions.forEach(subscription =>
      subscription.unsubscribe()
    );

    this.roomSubscriptions.clear();

    if (this.client?.active) {
      void this.client.deactivate();
    }

    this.connectedSubject.next(false);
  }

  private getWebSocketUrl(): string {
    const url = new URL(`${environment.apiUrl}/ws`, window.location.origin);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    return url.toString();
  }
}
