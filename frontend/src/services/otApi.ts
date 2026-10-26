import { apiClient } from './apiClient';
import { OtBookingDto, OtBookingRequest, OtRoomDto } from '../types';

export const otApi = {
  getTheatres: (): Promise<OtRoomDto[]> => {
    return apiClient.get<OtRoomDto[]>('/ot/theatres');
  },

  getBookings: (theatreId?: number, start?: string, end?: string): Promise<OtBookingDto[]> => {
    const params = new URLSearchParams();
    if (theatreId) params.append('theatreId', theatreId.toString());
    if (start) params.append('start', start);
    if (end) params.append('end', end);
    const query = params.toString() ? `?${params.toString()}` : '';
    return apiClient.get<OtBookingDto[]>(`/ot/bookings${query}`);
  },

  createBooking: (request: OtBookingRequest): Promise<OtBookingDto> => {
    return apiClient.post<OtBookingDto>('/ot/bookings', request);
  },

  cancelBooking: (bookingId: number, reason?: string): Promise<OtBookingDto> => {
    const q = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return apiClient.post<OtBookingDto>(`/ot/bookings/${bookingId}/cancel${q}`);
  },
};
