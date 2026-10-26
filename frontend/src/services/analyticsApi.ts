import { apiClient } from './apiClient';
import {
  BedDemandForecastDto,
  ClusterScanResultDto,
  CongestionForecastDto,
  FlowAnomalyDto,
  OutbreakAlertDto,
  WhatIfSimulationRequest,
  WhatIfSimulationResult,
} from '../types';

function unwrap<T>(res: any): T {
  if (res && typeof res === 'object' && 'data' in res && res.data !== undefined) {
    return res.data as T;
  }
  return res as T;
}

export const analyticsApi = {
  getEdCongestionForecast: async (): Promise<CongestionForecastDto> => {
    const raw = await apiClient.get<any>('/analytics/congestion-forecast');
    return unwrap<CongestionForecastDto>(raw);
  },

  getBedDemandForecast: async (): Promise<BedDemandForecastDto[]> => {
    const raw = await apiClient.get<any>('/analytics/bed-demand');
    const data = unwrap<BedDemandForecastDto[]>(raw);
    return Array.isArray(data) ? data : [];
  },

  detectFlowAnomalies: async (): Promise<FlowAnomalyDto[]> => {
    const raw = await apiClient.get<any>('/analytics/anomalies');
    const data = unwrap<FlowAnomalyDto[]>(raw);
    return Array.isArray(data) ? data : [];
  },

  runWhatIfSimulation: async (request: WhatIfSimulationRequest): Promise<WhatIfSimulationResult> => {
    const raw = await apiClient.post<any>('/analytics/simulate', request);
    return unwrap<WhatIfSimulationResult>(raw);
  },

  getActiveAlerts: async (): Promise<OutbreakAlertDto[]> => {
    const raw = await apiClient.get<any>('/sentinel/alerts');
    const data = unwrap<OutbreakAlertDto[]>(raw);
    return Array.isArray(data) ? data : [];
  },

  runSurveillanceScan: async (): Promise<ClusterScanResultDto> => {
    const raw = await apiClient.post<any>('/sentinel/scan');
    return unwrap<ClusterScanResultDto>(raw);
  },

  acknowledgeAlert: async (alertId: number): Promise<OutbreakAlertDto> => {
    const raw = await apiClient.post<any>(`/sentinel/alerts/${alertId}/ack`);
    return unwrap<OutbreakAlertDto>(raw);
  },
};
