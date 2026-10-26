// Standard API Envelope
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  timestamp: string;
}

export interface ApiError {
  success: false;
  error: {
    code: string;
    message: string;
    details?: string[];
  };
  timestamp: string;
}

// Authentication & Users
export type Role = 'ADMIN' | 'DOCTOR' | 'NURSE' | 'PHARMACIST' | 'RECEPTIONIST';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  fullName: string;
  role: Role;
  expiresInSeconds: number;
}

export interface UserPrincipal {
  userId: number;
  username: string;
  fullName: string;
  role: Role;
}

// Patient & Encounter Flow
export type VisitType = 'EMERGENCY' | 'INPATIENT' | 'OUTPATIENT';
export type VisitStatus = 'TRIAGE_WAIT' | 'IN_TREATMENT' | 'ADMITTED' | 'DISCHARGED' | 'TRANSFERRED';

export interface PatientRegistrationDto {
  firstName: string;
  lastName: string;
  dateOfBirth: string; // YYYY-MM-DD
  gender: 'MALE' | 'FEMALE' | 'OTHER';
  phone?: string;
  emergencyContact?: string;
}

export interface PatientResponseDto {
  id: number;
  mrn: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  phone?: string;
  emergencyContact?: string;
  createdAt: string;
}

export interface VisitCreateDto {
  patientId: number;
  visitType: VisitType;
  chiefComplaint: string;
}

export interface VisitResponseDto {
  id: number;
  visitNumber: string;
  patientId: number;
  patientName: string;
  mrn: string;
  visitType: VisitType;
  status: VisitStatus;
  arrivalTime: string;
  dischargeTime?: string;
  chiefComplaint: string;
  assignedDoctorName?: string;
}

export interface ActiveEncounterDto {
  visitId: number;
  visitNumber: string;
  patientId: number;
  patientName: string;
  firstName?: string;
  lastName?: string;
  mrn: string;
  status: VisitStatus;
  visitType: VisitType;
  arrivalTime: string;
  chiefComplaint?: string;
  esiLevel?: number | null;
  assignedDoctorId?: number | null;
  assignedDoctorName?: string | null;
  patient?: {
    id?: number;
    mrn?: string;
    firstName?: string;
    lastName?: string;
  };
  heartRate?: number | null;
  bloodPressureSys?: number | null;
  bloodPressureDia?: number | null;
  respiratoryRate?: number | null;
  temperatureCelsius?: number | null;
  temperatureFahrenheit?: number | null;
  o2Saturation?: number | null;
  painScore?: number | null;
  consciousnessLevel?: string | null;
  triageNotes?: string | null;
  symptomNarrative?: string | null;
  symptomDuration?: string | null;
  triageTime?: string | null;
  triagedByName?: string | null;
  age?: number | null;
  gender?: string | null;
  medicalHistory?: string | null;
  physicalExam?: string | null;
  triageOrders?: string | null;
  patientNarrative?: string | null;
}

export interface TriageAssessmentDto {
  visitId: number;
  esiLevel: number; // 1 to 5
  heartRate: number;
  bloodPressureSys: number;
  bloodPressureDia: number;
  respiratoryRate: number;
  temperatureCelsius: number;
  o2Saturation: number;
  consciousnessLevel: string; // 'ALERT', 'VERBAL', 'PAIN', 'UNRESPONSIVE'
  triageNotes?: string;
}

export interface TriageAssessmentResponseDto {
  id: number;
  visitId: number;
  visitNumber: string;
  patientName: string;
  mrn: string;
  chiefComplaint: string;
  esiLevel: number;
  heartRate: number;
  bloodPressureSys: number;
  bloodPressureDia: number;
  respiratoryRate: number;
  temperatureCelsius: number;
  o2Saturation: number;
  consciousnessLevel: string;
  triageNotes?: string;
  triagedByName: string;
  triageTime: string;
}

// Bed Management
export type BedStatus = 'AVAILABLE' | 'OCCUPIED' | 'CLEANING' | 'MAINTENANCE';
export type BedType = 'STANDARD' | 'MONITORED' | 'ICU_VENTILATOR' | 'ISOLATION';

export interface Bed {
  id: number;
  wardId: number;
  wardCode: string;
  wardName: string;
  bedNumber: string;
  bedType: BedType;
  status: BedStatus;
  currentPatientName?: string;
  currentVisitId?: number;
  currentVisitNumber?: string;
  occupiedSince?: string;
  lastCleanedAt?: string;
}

export interface WardBedSummary {
  wardId: number;
  wardCode: string;
  wardName: string;
  department: string;
  totalBeds: number;
  availableBeds: number;
  occupiedBeds: number;
  cleaningBeds: number;
  maintenanceBeds: number;
  occupancyRate: number;
}

export interface BedCapacitySummaryDto {
  totalBeds: number;
  totalHospitalBeds?: number;
  availableBeds: number;
  occupiedBeds: number;
  cleaningBeds: number;
  maintenanceBeds?: number;
  occupancyRate: number;
  overallOccupancyRate?: number;
  wardSummaries: WardBedSummary[];
  occupiedByWard?: Record<string, number>;
  availableByWard?: Record<string, number>;
}

export interface BedAllocationDto {
  bedId: number;
  visitId: number;
  notes?: string;
}

// Operating Theatre & Staff Scheduling
export type OtBookingStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface OtRoomDto {
  id: number;
  roomNumber: string;
  name: string;
  active: boolean;
  cleaningBufferMinutes: number;
}

export interface OtBookingDto {
  id: number;
  theatreId: number;
  theatreName: string;
  roomNumber: string;
  visitId: number;
  visitNumber: string;
  patientName: string;
  leadSurgeonId: number;
  leadSurgeonName: string;
  procedureName: string;
  scheduledStart: string;
  scheduledEnd: string;
  actualStart?: string;
  actualEnd?: string;
  status: OtBookingStatus;
  notes?: string;
}

export interface OtBookingRequest {
  theatreId: number;
  visitId: number;
  leadSurgeonId: number;
  procedureName: string;
  scheduledStart: string;
  scheduledEnd: string;
  emergencyOverride?: boolean;
  notes?: string;
}

export type ShiftType = 'DAY' | 'EVENING' | 'NIGHT' | 'ON_CALL';

export interface StaffDto {
  id: number;
  userId: number;
  fullName: string;
  username: string;
  staffCode: string;
  department: string;
  specialty?: string;
  qualification?: string;
  maxWeeklyHours: number;
}

export interface ShiftDto {
  id: number;
  staffId: number;
  staffCode: string;
  staffName: string;
  wardId?: number;
  wardCode?: string;
  shiftDate: string; // YYYY-MM-DD
  startTime: string; // HH:mm
  endTime: string;   // HH:mm
  shiftType: ShiftType;
  roleInShift?: string;
  cancelled: boolean;
}

export interface ShiftAssignmentDto {
  staffId: number;
  wardId?: number;
  shiftDate: string;
  startTime: string;
  endTime: string;
  shiftType: ShiftType;
  roleInShift?: string;
}

// Pharmacy & Medication Safety
export type InteractionSeverity = 'MILD' | 'MODERATE' | 'SEVERE_CONTRAINDICATED';
export type OrderStatus = 'ACTIVE' | 'DISCONTINUED' | 'COMPLETED';

export interface MedicationDto {
  id: number;
  code: string;
  genericName: string;
  brandName: string;
  dosageForm: string;
  strength: string;
  maxSingleDoseMg?: number;
  unit?: string;
}

export interface SafetyAlertDto {
  ruleId: number;
  primaryMedName: string;
  interactingMedName: string;
  severity: InteractionSeverity;
  clinicalEffect: string;
  managementAdvice: string;
}

export interface SafetyCheckResultDto {
  safe: boolean;
  requiresOverride: boolean;
  hardBlocked: boolean;
  alerts: SafetyAlertDto[];
  summaryMessage: string;
}

export interface MedicationOrderDto {
  id: number;
  visitId: number;
  medicationId: number;
  medicationName: string;
  medicationCode?: string;
  genericName?: string;
  brandName?: string;
  medicationStrength?: string;
  prescribedByName?: string;
  prescribedByDoctorName?: string;
  dose: string;
  numericDose?: number;
  route: string;
  frequency: string;
  status: OrderStatus;
  interactionOverridden: boolean;
  overrideReason?: string;
  discontinuedAt?: string;
  discontinueReason?: string;
  discontinuedByDoctorName?: string;
  createdAt: string;
}

export interface MedicationPrescribeRequest {
  visitId: number;
  medicationId: number;
  dose: string;
  numericDose?: number;
  route: string;
  frequency: string;
  interactionOverridden?: boolean;
  overrideReason?: string;
}

export interface MedicationSafetyCheckRequest {
  visitId?: number;
  proposedMedicationIds: number[];
}

// Clinical AI SOAP Assistant
export interface ClinicalNoteRequest {
  visitId: number;
  rawClinicalNotes: string;
}

export interface StructuredSoapResponse {
  subjective: string;
  objective: string;
  assessment: string;
  plan: string;
  triageCategoryDraft?: string;
  differentialDiagnoses: string[];
  suggestedInvestigations: string[];
  suggestedInterventions: string[];
  needsClinicianReview: boolean;
  safetyNotice: string;
}

export interface SoapSignoffRequest {
  subjective: string;
  objective: string;
  assessment: string;
  plan: string;
  triageCategory?: string;
  differentialDiagnoses?: string[];
  suggestedInvestigations?: string[];
  suggestedInterventions?: string[];
}

export interface ClinicalNoteDto {
  id: number;
  visitId: number;
  visitNumber: string;
  authorId: number;
  authorName: string;
  rawText: string;
  noteType: string;
  status: 'DRAFT' | 'AI_PROCESSED' | 'CLINICIAN_SIGNED';
  createdAt: string;
  signedAt?: string;
  signedById?: number;
  signedByName?: string;
}

export interface PatientMedicationInstructionDto {
  medicationName: string;
  dose: string;
  frequency: string;
  instructions: string;
}

export interface PatientCareSummaryDto {
  diagnosis: string;
  medications: PatientMedicationInstructionDto[];
  homeCare: string[];
  redFlags: string[];
  followUp: string;
  doctorSignature?: string;
  doctorName?: string;
  signedAt?: string;
  patientName?: string;
  mrn?: string;
  visitNumber?: string;
  visitDate?: string;
}

// Analytics, Simulation & Outbreak Sentinel
export interface CongestionForecastDto {
  targetTime: string;
  estimatedWaitMinutes: number;
  predictedQueueDepth: number;
  severityLevel: 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';
  modelSource: string;
}

export interface BedDemandForecastDto {
  targetTime: string;
  wardCode: string;
  wardName: string;
  currentOccupied: number;
  currentAvailable: number;
  predictedAdmissionsNext24h: number;
  netBedBalance: number;
  shortageRiskLevel: 'LOW' | 'MODERATE' | 'CRITICAL';
  confidenceIntervalLower: number;
  confidenceIntervalUpper: number;
}

export interface FlowAnomalyDto {
  id: string;
  visitId?: number;
  visitNumber: string;
  patientName: string;
  anomalyType: string;
  anomalyScore: number;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  detectedAt: string;
  description: string;
}

export interface WhatIfSimulationRequest {
  simulationHours: number;
  deltaArrivalRatePercent: number;
  deltaStaffingPercent: number;
  closedBedsCount: number;
}

export interface HourlyProjection {
  hour: number;
  simulatedArrivals: number;
  queueBacklog: number;
  bedsOccupied: number;
  bedsAvailable: number;
  estimatedWaitTimeMinutes: number;
  deficitRisk: boolean;
}

export interface WhatIfSimulationResult {
  deltaArrivalRatePercent: number;
  deltaStaffingPercent: number;
  closedBedsCount: number;
  simulationHours: number;
  overallBedDeficitProbability: number;
  peakQueueDepth: number;
  projectedAverageWaitMinutes: number;
  systemStressLevel: 'OPTIMAL' | 'STRAINED' | 'SATURATED' | 'COLLAPSED';
  hourlyProjections: HourlyProjection[];
  mitigationRecommendations: string[];
}

export type AlertLevel = 'WATCH' | 'WARNING' | 'OUTBREAK_TRIGGER';

export interface OutbreakAlertDto {
  id: number;
  syndromeCluster: string;
  detectedCount: number;
  baselineThreshold: number;
  zScore: number;
  alertLevel: AlertLevel;
  sampleVisitIds: string;
  acknowledged: boolean;
  createdAt: string;
}

export interface ClusterScanResultDto {
  totalVisitsScanned: number;
  syndromeDistribution: Record<string, number>;
  generatedAlerts: OutbreakAlertDto[];
  scanSummary: string;
}
