import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InviteUser } from './invite-user';

describe('InviteUser', () => {
  let component: InviteUser;
  let fixture: ComponentFixture<InviteUser>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InviteUser]
    })
    .compileComponents();

    fixture = TestBed.createComponent(InviteUser);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
