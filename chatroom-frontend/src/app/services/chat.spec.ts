import { Client, IFrame, StompSubscription } from '@stomp/stompjs';
import { ChatService } from './chat';

describe('ChatService subscriptions', () => {
  let service: ChatService;
  let client: Client;
  let subscribe: jasmine.Spy;
  let previousToken: string | null;

  beforeEach(() => {
    previousToken = localStorage.getItem('token');
    localStorage.setItem('token', 'test-token');
    spyOn(Client.prototype, 'activate');
    service = new ChatService();
    service.connect();
    client = (service as unknown as { client: Client }).client;
    spyOnProperty(client, 'connected', 'get').and.returnValue(true);
    subscribe = spyOn(client, 'subscribe').and.returnValue({
      id: 'test-subscription', unsubscribe: jasmine.createSpy('unsubscribe')
    } as StompSubscription);
  });

  afterEach(() => {
    service.disconnect();
    if (previousToken === null) localStorage.removeItem('token');
    else localStorage.setItem('token', previousToken);
  });

  it('keeps only one subscription per room on the same connection', () => {
    service.subscribeToRoom(7);
    service.subscribeToRoom(7);
    expect(subscribe).toHaveBeenCalledTimes(1);
  });

  it('subscribes again when the STOMP connection is restored', () => {
    service.subscribeToRoom(7);
    client.onWebSocketClose({} as CloseEvent);
    client.onConnect({} as IFrame);
    service.subscribeToRoom(7);
    expect(subscribe).toHaveBeenCalledTimes(2);
  });
});
