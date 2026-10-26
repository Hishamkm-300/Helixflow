import { apiClient } from './apiClient';
import { LoginRequest, LoginResponse, UserPrincipal } from '../types';

export const authApi = {
  login: (request: LoginRequest): Promise<LoginResponse> => {
    return apiClient.post<LoginResponse>('/auth/login', request);
  },

  getCurrentUser: (): Promise<UserPrincipal> => {
    return apiClient.get<UserPrincipal>('/auth/me');
  },

  logout: (): Promise<void> => {
    return apiClient.post<void>('/auth/logout');
  },
};
