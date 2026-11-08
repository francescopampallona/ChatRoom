import { TestBed } from '@angular/core/testing';

import { Invite } from './invite';

describe('Invite', () => {
  let service: Invite;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Invite);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
