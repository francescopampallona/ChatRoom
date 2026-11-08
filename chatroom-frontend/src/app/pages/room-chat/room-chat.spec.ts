import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RoomChat } from './room-chat';

describe('RoomChat', () => {
  let component: RoomChat;
  let fixture: ComponentFixture<RoomChat>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RoomChat]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RoomChat);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
