import { apiClient } from './apiClient';
import { Bed, BedAllocationDto, BedCapacitySummaryDto, BedStatus } from '../types';

function unwrap<T>(res: any): T {
  if (res && typeof res === 'object' && 'data' in res && res.data !== undefined) {
    return res.data as T;
  }
  return res as T;
}

export const bedApi = {
  getBeds: async (wardId?: number, status?: BedStatus): Promise<Bed[]> => {
    const params = new URLSearchParams();
    if (wardId) params.append('wardId', wardId.toString());
    if (status) params.append('status', status);
    const query = params.toString() ? `?${params.toString()}` : '';
    const raw = await apiClient.get<any>(`/beds${query}`);
    const data = unwrap<Bed[]>(raw);
    return Array.isArray(data) ? data : [];
  },

  getAllBeds: async (wardId?: number, status?: BedStatus): Promise<Bed[]> => {
    return bedApi.getBeds(wardId, status);
  },

  getSummary: async (): Promise<BedCapacitySummaryDto> => {
    const raw = await apiClient.get<any>('/beds/summary');
    const data = unwrap<any>(raw);
    
    const total = data?.totalBeds ?? data?.totalHospitalBeds ?? 0;
    const rate = data?.occupancyRate ?? data?.overallOccupancyRate ?? 0;

    return {
      totalBeds: total,
      totalHospitalBeds: total,
      availableBeds: data?.availableBeds ?? 0,
      occupiedBeds: data?.occupiedBeds ?? 0,
      cleaningBeds: data?.cleaningBeds ?? 0,
      maintenanceBeds: data?.maintenanceBeds ?? 0,
      occupancyRate: rate,
      overallOccupancyRate: rate,
      wardSummaries: Array.isArray(data?.wardSummaries) ? data.wardSummaries : [],
      occupiedByWard: data?.occupiedByWard ?? {},
      availableByWard: data?.availableByWard ?? {},
    };
  },

  getCapacitySummary: async (): Promise<BedCapacitySummaryDto> => {
    return bedApi.getSummary();
  },

  admitPatient: async (dto: BedAllocationDto): Promise<Bed> => {
    const raw = await apiClient.post<any>('/beds/admit', dto);
    return unwrap<Bed>(raw);
  },

  dischargeBed: async (bedId: number): Promise<Bed> => {
    const raw = await apiClient.post<any>(`/beds/${bedId}/discharge`);
    return unwrap<Bed>(raw);
  },

  markCleaned: async (bedId: number): Promise<Bed> => {
    const raw = await apiClient.post<any>(`/beds/${bedId}/cleaned`);
    return unwrap<Bed>(raw);
  },
};
