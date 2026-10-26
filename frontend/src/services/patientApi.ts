import { apiClient } from './apiClient';
import {
  PatientRegistrationDto,
  PatientResponseDto,
  VisitCreateDto,
  VisitResponseDto,
  TriageAssessmentDto,
  TriageAssessmentResponseDto,
} from '../types';

export const patientApi = {
  searchPatients: (query?: string): Promise<PatientResponseDto[]> => {
    const q = query ? `?query=${encodeURIComponent(query)}` : '';
    return apiClient.get<PatientResponseDto[]>(`/patients${q}`);
  },

  registerPatient: (dto: PatientRegistrationDto): Promise<PatientResponseDto> => {
    return apiClient.post<PatientResponseDto>('/patients', dto);
  },

  createVisit: (patientId: number, dto: VisitCreateDto): Promise<VisitResponseDto> => {
    return apiClient.post<VisitResponseDto>(`/patients/${patientId}/visits`, dto);
  },

  getPatientVisits: (patientId: number): Promise<VisitResponseDto[]> => {
    return apiClient.get<VisitResponseDto[]>(`/patients/${patientId}/visits`);
  },

  getTriageQueue: (): Promise<TriageAssessmentResponseDto[]> => {
    return apiClient.get<TriageAssessmentResponseDto[]>('/triage/queue');
  },

  submitTriage: (dto: TriageAssessmentDto): Promise<TriageAssessmentResponseDto> => {
    return apiClient.post<TriageAssessmentResponseDto>('/triage', dto);
  },
};
