import { UserDto } from "./UserDto";

export interface AuthResponse {
  token: string;
  user: UserDto;
}