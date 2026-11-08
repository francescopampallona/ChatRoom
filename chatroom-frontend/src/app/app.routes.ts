import { Routes } from '@angular/router';
import { RegisterComponent } from './pages/register/register'
import { LoginComponent } from './pages/login/login';
import { HomeComponent } from './pages/home/home';
import { authGuard } from './guards/auth-guard';
import { CreateRoomComponent } from './pages/create-room/create-room';
import { InviteUserComponent } from './pages/invite-user/invite-user';
import { MyInvitesComponent } from './pages/my-invites/my-invites';
import { RoomChatComponent } from './pages/room-chat/room-chat';

export const routes: Routes = [
  {
    path: 'register',
    component: RegisterComponent
  },
  {
    path:'login',
    component: LoginComponent
  },
  {
    path:'home',
    component: HomeComponent,
    canActivate: [authGuard]
  },
  {
  path: 'rooms/create',
  component: CreateRoomComponent,
  canActivate: [authGuard]
  },
  {
   path: 'rooms/:roomId/invite',
   component: InviteUserComponent,
   canActivate: [authGuard]
  },
  {
  path: 'rooms/:roomId/chat',
  component: RoomChatComponent,
  canActivate: [authGuard]
  },
  {
  path: 'invites',
  component: MyInvitesComponent,
  canActivate: [authGuard]
  },
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  }
];