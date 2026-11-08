import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MyInvites } from './my-invites';

describe('MyInvites', () => {
  let component: MyInvites;
  let fixture: ComponentFixture<MyInvites>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyInvites]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MyInvites);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
