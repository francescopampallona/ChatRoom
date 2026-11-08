import { fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { AiRoomStatus } from '../../dto/AiRoomStatus';
import { MessageResponse } from '../../dto/MessageResponse';
import { PageResponse } from '../../dto/PageResponse';
import { RoomDto } from '../../dto/RoomDto';
import { AiRoomService } from '../../services/ai-room';
import { ChatService } from '../../services/chat';
import { MessageService } from '../../services/message';
import { RoomService } from '../../services/room';
import { RoomChatComponent } from './room-chat';

const status = (overrides: Partial<AiRoomStatus> = {}): AiRoomStatus => ({
  enabled: true, ready: true, running: false, generating: false,
  remainingTurns: 0, model: 'local', error: null, agents: [], ...overrides
});
const message = (id: number): MessageResponse => ({
  id, roomId: 7, senderId: 11, senderUsername: 'aria.ai', senderIsAgent: true,
  content: `Message ${id}`, sentAt: `2026-11-08T12:00:0${id}`
});
const page = (content: MessageResponse[]): PageResponse<MessageResponse> => ({
  content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true
});

describe('RoomChatComponent AI conversation', () => {
  let component: RoomChatComponent;
  let room: RoomDto;
  let connected: BehaviorSubject<boolean>;
  let messages: Subject<MessageResponse>;
  let chat: jasmine.SpyObj<ChatService>;
  let rooms: jasmine.SpyObj<RoomService>;
  let history: jasmine.SpyObj<MessageService>;
  let ai: jasmine.SpyObj<AiRoomService>;

  beforeEach(() => {
    room = {
      id: 7, name: 'AI Lounge', description: '', type: 'PUBLIC', aiRoom: true,
      ownerId: 11, ownerUsername: 'aria.ai', createdAt: '2026-11-08T12:00:00'
    };
    connected = new BehaviorSubject(false);
    messages = new Subject<MessageResponse>();
    chat = jasmine.createSpyObj<ChatService>('ChatService',
      ['connect', 'subscribeToRoom', 'unsubscribeFromRoom', 'sendMessage'],
      { connected$: connected, messages$: messages });
    rooms = jasmine.createSpyObj<RoomService>('RoomService', ['getRoomById']);
    rooms.getRoomById.and.callFake(() => of(room));
    history = jasmine.createSpyObj<MessageService>('MessageService', ['getMessages']);
    history.getMessages.and.returnValue(of(page([])));
    ai = jasmine.createSpyObj<AiRoomService>('AiRoomService', ['getStatus', 'setRunning', 'requestTurn']);
    ai.getStatus.and.returnValue(of(status()));
    ai.setRunning.and.callFake((_id, running) => of(status({ running, remainingTurns: running ? 6 : 0 })));
    ai.requestTurn.and.returnValue(of(status({ generating: true })));
    component = new RoomChatComponent(
      { snapshot: { paramMap: { get: () => '7' } } } as unknown as ActivatedRoute,
      jasmine.createSpyObj<Router>('Router', ['navigate']), chat, rooms, history, ai
    );
  });

  afterEach(() => {
    component.ngOnDestroy();
    connected.complete();
    messages.complete();
  });

  it('never polls ordinary rooms', fakeAsync(() => {
    room.aiRoom = false;
    component.ngOnInit();
    tick(12000);
    expect(ai.getStatus).not.toHaveBeenCalled();
    component.ngOnDestroy();
  }));

  it('does not overlap polls and cancels polling on leaving the room', fakeAsync(() => {
    const pending = new Subject<AiRoomStatus>();
    ai.getStatus.and.returnValue(pending);
    component.ngOnInit();
    tick(0);
    tick(8000);
    expect(ai.getStatus).toHaveBeenCalledTimes(1);
    pending.next(status());
    pending.complete();
    tick(4000);
    expect(ai.getStatus).toHaveBeenCalledTimes(2);
    component.ngOnDestroy();
    tick(12000);
    expect(ai.getStatus).toHaveBeenCalledTimes(2);
    expect(chat.unsubscribeFromRoom).toHaveBeenCalledWith(7);
  }));

  it('recovers from a failed status request on the next poll', fakeAsync(() => {
    ai.getStatus.and.returnValues(throwError(() => new Error('offline')), of(status()));
    component.ngOnInit();
    tick(0);
    expect(component.aiStatusError).toBeTruthy();
    tick(4000);
    expect(component.aiStatusError).toBe('');
    expect(component.aiStatusLabel).toBe('In pausa');
    component.ngOnDestroy();
  }));

  it('does not overwrite a pause command with an older poll result', fakeAsync(() => {
    const pending = new Subject<AiRoomStatus>();
    ai.getStatus.and.returnValue(pending);
    component.ngOnInit();
    component.aiStatus = status({ running: true });
    tick(0);
    component.setAiRunning(false);
    pending.next(status({ running: true }));
    pending.complete();
    expect(component.aiStatus?.running).toBeFalse();
    component.ngOnDestroy();
  }));

  it('can start a new round while listening', () => {
    component.roomId = 7;
    component.aiStatus = status({ running: true, remainingTurns: 0 });
    expect(component.aiStatusLabel).toBe('In ascolto');
    component.setAiRunning(true);
    expect(ai.setRunning).toHaveBeenCalledWith(7, true);
    expect(component.aiStatus?.remainingTurns).toBe(6);
  });

  it('prevents repeated one-turn requests and shows a friendly conflict error', () => {
    const pending = new Subject<AiRoomStatus>();
    ai.requestTurn.and.returnValue(pending);
    component.roomId = 7;
    component.aiStatus = status();
    component.requestAiTurn();
    component.requestAiTurn();
    expect(ai.requestTurn).toHaveBeenCalledTimes(1);
    expect(component.aiBusy).toBeTrue();
    pending.error({ status: 409 });
    expect(component.aiBusy).toBeFalse();
    expect(component.aiActionError).toContain('già preparando');
    expect(component.aiStatus?.running).toBeFalse();
  });

  it('keeps live bot replies when history arrives and deduplicates by message ID', fakeAsync(() => {
    room.aiRoom = false;
    const pendingHistory = new Subject<PageResponse<MessageResponse>>();
    history.getMessages.and.returnValue(pendingHistory);
    component.ngOnInit();
    messages.next(message(2));
    pendingHistory.next(page([message(2), message(1)]));
    pendingHistory.complete();
    messages.next(message(2));
    messages.next({ ...message(3), roomId: 999 });
    expect(component.messages.map(value => value.id)).toEqual([1, 2]);
    expect(component.messages[1].senderIsAgent).toBeTrue();
    component.ngOnDestroy();
  }));

  it('reloads missed replies after a reconnect without dropping existing replies', fakeAsync(() => {
    room.aiRoom = false;
    history.getMessages.and.returnValues(of(page([message(1)])), of(page([message(3), message(2)])));
    component.ngOnInit();
    connected.next(true);
    expect(chat.subscribeToRoom).toHaveBeenCalledWith(7);
    expect(component.messages.map(value => value.id)).toEqual([1, 2, 3]);
    component.ngOnDestroy();
  }));
});
