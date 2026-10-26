import { apiClient } from './apiClient';
import {
  ActiveEncounterDto,
  ClinicalNoteDto,
  ClinicalNoteRequest,
  SoapSignoffRequest,
  StructuredSoapResponse,
} from '../types';

export const clinicalApi = {
  getActiveEncounters: (): Promise<ActiveEncounterDto[]> => {
    return apiClient.get<ActiveEncounterDto[]>('/clinical/encounters');
  },
  getEncounterDetails: (visitId: number): Promise<ActiveEncounterDto> => {
    return apiClient.get<ActiveEncounterDto>(`/clinical/encounters/${visitId}`);
  },
  structureClinicalNote: (request: ClinicalNoteRequest): Promise<StructuredSoapResponse> => {
    return apiClient.post<StructuredSoapResponse>('/clinical/soap/structure', request);
  },
  saveNoteDraft: (visitId: number, rawText: string, noteType = 'PROGRESS_NOTE'): Promise<ClinicalNoteDto> => {
    return apiClient.post<ClinicalNoteDto>(`/clinical/notes/${visitId}/draft`, { rawText, noteType });
  },
  signoffSoapNote: (noteId: number, payload: SoapSignoffRequest): Promise<ClinicalNoteDto> => {
    return apiClient.put<ClinicalNoteDto>(`/clinical/notes/${noteId}/soap-signoff`, payload);
  },
  signoffSoapAtomic: (visitId: number, payload: SoapSignoffRequest): Promise<ClinicalNoteDto> => {
    return apiClient.post<ClinicalNoteDto>(`/clinical/notes/${visitId}/soap-signoff-atomic`, payload);
  },
  generatePatientSummary: (noteId: number): Promise<import('../types').PatientCareSummaryDto> => {
    return apiClient.post<import('../types').PatientCareSummaryDto>(`/clinical/notes/${noteId}/patient-summary`, {});
  },
  generatePatientSummaryAdhoc: (request: { visitId?: number; noteId?: number; assessment?: string; plan?: string }): Promise<import('../types').PatientCareSummaryDto> => {
    return apiClient.post<import('../types').PatientCareSummaryDto>('/clinical/patient-summary', request);
  },
};
