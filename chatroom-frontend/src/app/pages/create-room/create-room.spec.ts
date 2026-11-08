import { Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { RoomDto } from '../../dto/RoomDto';
import { AiRoomService } from '../../services/ai-room';
import { RoomService } from '../../services/room';
import { CreateRoomComponent } from './create-room';

describe('CreateRoomComponent themed AI room', () => {
  let component: CreateRoomComponent;
  let rooms: jasmine.SpyObj<RoomService>;
  let ai: jasmine.SpyObj<AiRoomService>;
  let router: jasmine.SpyObj<Router>;
  const room: RoomDto = {
    id: 23, name: 'Missione Marte', description: 'Coltivare nello spazio', type: 'PRIVATE',
    aiRoom: false, ownerId: 1, ownerUsername: 'owner', createdAt: '2026-11-08T12:00:00'
  };

  beforeEach(() => {
    rooms = jasmine.createSpyObj<RoomService>('RoomService', ['createRoom']);
    rooms.createRoom.and.returnValue(of(room));
    ai = jasmine.createSpyObj<AiRoomService>('AiRoomService', ['getAgents']);
    ai.getAgents.and.returnValue(of({ enabled: true, agents: [] }));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    component = new CreateRoomComponent(rooms, router, ai);
  });

  afterEach(() => component.ngOnDestroy());

  it('creates the themed private room with the selected agents and opens its chat', () => {
    component.ngOnInit();
    component.form = { name: ' Missione Marte ', description: ' Coltivare nello spazio ', type: 'PRIVATE', agentKeys: ['aria', 'mira'] };
    component.createRoom();
    expect(rooms.createRoom).toHaveBeenCalledWith({
      name: 'Missione Marte', description: 'Coltivare nello spazio', type: 'PRIVATE', agentKeys: ['aria', 'mira']
    });
    expect(router.navigate).toHaveBeenCalledWith(['/rooms', 23, 'chat']);
  });

  it('still permits creating a normal room if the agent catalog is unavailable', () => {
    ai.getAgents.and.returnValue(throwError(() => new Error('offline')));
    component.ngOnInit();
    expect(component.agentsError).toContain('invitarli in seguito');
    component.form.name = 'Astronomia';
    component.createRoom();
    expect(rooms.createRoom).toHaveBeenCalledWith({ name: 'Astronomia', description: '', type: 'PUBLIC', agentKeys: [] });
  });

  it('prevents duplicate room creation while the first request is pending', () => {
    const pending = new Subject<RoomDto>();
    rooms.createRoom.and.returnValue(pending);
    component.form.name = 'Astronomia';
    component.createRoom();
    component.createRoom();
    expect(rooms.createRoom).toHaveBeenCalledTimes(1);
    pending.error({ error: { message: 'Riprova' } });
    expect(component.loading).toBeFalse();
    expect(component.form.name).toBe('Astronomia');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('keeps the topic and selected agents when creation fails', () => {
    rooms.createRoom.and.returnValue(throwError(() => ({ error: { message: 'Agenti non disponibili' } })));
    component.form = { name: 'Marte', description: 'Missioni spaziali', type: 'PUBLIC', agentKeys: ['bruno'] };
    component.createRoom();
    expect(component.form.agentKeys).toEqual(['bruno']);
    expect(component.form.description).toBe('Missioni spaziali');
    expect(component.errorMessage).toBe('Agenti non disponibili');
  });
});
