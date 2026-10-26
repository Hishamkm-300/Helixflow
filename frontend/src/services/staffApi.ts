import { apiClient } from './apiClient';
import { ShiftAssignmentDto, ShiftDto, StaffDto } from '../types';

export const staffApi = {
  getAllStaff: (): Promise<StaffDto[]> => {
    return apiClient.get<StaffDto[]>('/staff');
  },

  getRoster: (startDate?: string, endDate?: string): Promise<ShiftDto[]> => {
    const params = new URLSearchParams();
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);
    const query = params.toString() ? `?${params.toString()}` : '';
    return apiClient.get<ShiftDto[]>(`/staff/roster${query}`);
  },

  assignShift: (dto: ShiftAssignmentDto): Promise<ShiftDto> => {
    return apiClient.post<ShiftDto>('/staff/shifts', dto);
  },

  cancelShift: (shiftId: number): Promise<ShiftDto> => {
    return apiClient.post<ShiftDto>(`/staff/shifts/${shiftId}/cancel`);
  },
};
