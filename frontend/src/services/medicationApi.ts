import { apiClient } from './apiClient';
import {
  MedicationDto,
  MedicationOrderDto,
  MedicationPrescribeRequest,
  MedicationSafetyCheckRequest,
  SafetyCheckResultDto,
} from '../types';

export const medicationApi = {
  searchMedications: (query?: string): Promise<MedicationDto[]> => {
    const q = query ? `?query=${encodeURIComponent(query)}` : '';
    return apiClient.get<MedicationDto[]>(`/medications${q}`);
  },

  getActiveOrdersForVisit: (visitId: number): Promise<MedicationOrderDto[]> => {
    return apiClient.get<MedicationOrderDto[]>(`/medications/orders/${visitId}`);
  },

  evaluateSafety: (request: MedicationSafetyCheckRequest): Promise<SafetyCheckResultDto> => {
    return apiClient.post<SafetyCheckResultDto>('/medications/safety-check', request);
  },

  prescribeMedication: (request: MedicationPrescribeRequest): Promise<MedicationOrderDto> => {
    return apiClient.post<MedicationOrderDto>('/medications/prescribe', request);
  },

  discontinueOrder: (orderId: number, reason: string): Promise<MedicationOrderDto> => {
    return apiClient.put<MedicationOrderDto>(`/medications/orders/${orderId}/discontinue`, { reason });
  },
};
