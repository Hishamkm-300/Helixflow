import React, { useState, useEffect } from 'react';
import {
  Users,
  BedDouble,
  Clock,
  Activity,
  AlertTriangle,
  TrendingUp,
  RefreshCw,
  ShieldAlert,
  ArrowUpRight,
  CheckCircle2,
} from 'lucide-react';
import { bedApi } from '../../services/bedApi';
import { patientApi } from '../../services/patientApi';
import { analyticsApi } from '../../services/analyticsApi';
import { otApi } from '../../services/otApi';
import { queryClient } from '../../services/queryCache';
import {
  BedCapacitySummaryDto,
  CongestionForecastDto,
  BedDemandForecastDto,
  FlowAnomalyDto,
  TriageAssessmentResponseDto,
  OtBookingDto,
} from '../../types';
import './DashboardScreen.css';

export const DashboardScreen: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [bedSummary, setBedSummary] = useState<BedCapacitySummaryDto | null>(null);
  const [triageQueue, setTriageQueue] = useState<TriageAssessmentResponseDto[]>([]);
  const [congestion, setCongestion] = useState<CongestionForecastDto | null>(null);
  const [bedDemand, setBedDemand] = useState<BedDemandForecastDto[]>([]);
  const [anomalies, setAnomalies] = useState<FlowAnomalyDto[]>([]);
  const [otBookings, setOtBookings] = useState<OtBookingDto[]>([]);

  const loadDashboardData = async () => {
    try {
      const [beds, triage, cong, demand, anom, bookings] = await Promise.all([
        bedApi.getCapacitySummary().catch(() => null),
        patientApi.getTriageQueue().catch(() => []),
        analyticsApi.getEdCongestionForecast().catch(() => null),
        analyticsApi.getBedDemandForecast().catch(() => []),
        analyticsApi.detectFlowAnomalies().catch(() => []),
        otApi.getBookings().catch(() => []),
      ]);

      if (beds) setBedSummary(beds);
      setTriageQueue(triage);
      if (cong) setCongestion(cong);
      setBedDemand(demand);
      setAnomalies(anom);
      setOtBookings(bookings);
    } catch (err) {
      console.error('Failed to load dashboard metrics', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadDashboardData();
    // Subscribe to cross-module cache invalidations
    const unsubAnom = queryClient.subscribe('flow-anomalies', loadDashboardData);
    const unsubMetrics = queryClient.subscribe('command-center-metrics', loadDashboardData);
    const unsubQueue = queryClient.subscribe('triage-queue', loadDashboardData);

    const interval = setInterval(loadDashboardData, 30000); // Poll every 30s
    return () => {
      unsubAnom();
      unsubMetrics();
      unsubQueue();
      clearInterval(interval);
    };
  }, []);

  const handleRefresh = () => {
    setRefreshing(true);
    loadDashboardData();
  };

  const getSeverityBadgeClass = (severity?: string) => {
    switch (severity) {
      case 'CRITICAL': return 'badge-occupied';
      case 'HIGH': return 'badge-esi-2';
      case 'NORMAL':
      case 'MODERATE': return 'badge-cleaning';
      default: return 'badge-available';
    }
  };

  if (loading) {
    return (
      <div className="dashboard-loading">
        <Activity className="animate-spin text-cyan" size={32} />
        <p>Loading real-time hospital flow intelligence...</p>
      </div>
    );
  }

  const activeOtProcedures = otBookings.filter(b => b.status === 'IN_PROGRESS' || b.status === 'SCHEDULED').length;

  return (
    <div className="dashboard-container animate-fade-in">
      {/* Top Banner / Actions */}
      <div className="dashboard-header-bar">
        <div>
          <h1>Flow Command Center</h1>
          <p className="dashboard-subtitle">
            AI-monitored telemetry, bottleneck predictions & syndromic sentinel telemetry
          </p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={handleRefresh} disabled={refreshing}>
          <RefreshCw size={14} className={refreshing ? 'animate-spin' : ''} />
          <span>{refreshing ? 'Refreshing...' : 'Live Sync'}</span>
        </button>
      </div>

      {/* Safety Notice Banner */}
      <div className="disclaimer-banner">
        <ShieldAlert size={18} />
        <span>
          <strong>Decision-Support Notice:</strong> Predictions and anomaly scores are generated for administrative patient flow optimization and triage prioritization. Clinical determinations remain the sole responsibility of attending healthcare providers.
        </span>
      </div>

      {/* KPI Cards Row */}
      <div className="kpi-grid">
        <div className="stat-card">
          <div className="stat-header">
            <span>Hospital Bed Occupancy</span>
            <div className="stat-icon"><BedDouble size={20} /></div>
          </div>
          <div className="stat-value">
            {bedSummary ? `${bedSummary.occupancyRate ?? bedSummary.overallOccupancyRate ?? 0}%` : '0%'}
          </div>
          <div className="stat-desc">
            {bedSummary
              ? `${bedSummary.occupiedBeds ?? 0} occupied / ${bedSummary.totalBeds ?? bedSummary.totalHospitalBeds ?? 0} total beds`
              : '0 occupied / 0 total beds'}
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-header">
            <span>Triage Wait Backlog</span>
            <div className="stat-icon"><Users size={20} /></div>
          </div>
          <div className="stat-value">{triageQueue.length}</div>
          <div className="stat-desc">
            Patients waiting for physician & inpatient assignment
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-header">
            <span>Predicted ED Wait</span>
            <div className="stat-icon"><Clock size={20} /></div>
          </div>
          <div className="stat-value">
            {congestion ? `${Math.round(congestion.estimatedWaitMinutes)}m` : 'N/A'}
          </div>
          <div className="stat-desc">
            Congestion Level: <span className={`badge ${getSeverityBadgeClass(congestion?.severityLevel)}`}>{congestion?.severityLevel || 'NORMAL'}</span>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-header">
            <span>Scheduled Surgeries</span>
            <div className="stat-icon"><Activity size={20} /></div>
          </div>
          <div className="stat-value">{activeOtProcedures}</div>
          <div className="stat-desc">
            Across 3 active operating theatre suites
          </div>
        </div>
      </div>

      {/* Main Insights Grid */}
      <div className="insights-grid">
        {/* Ward Bed Demand Projections */}
        <div className="card">
          <div className="card-header">
            <div className="card-title">
              <TrendingUp size={18} />
              <span>24-Hour Ward Bed Demand Forecast</span>
            </div>
            <span className="badge badge-ai">Smile ML Model</span>
          </div>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Ward</th>
                  <th>Occupied</th>
                  <th>Available</th>
                  <th>Projected Admissions</th>
                  <th>Net Balance</th>
                  <th>Shortage Risk</th>
                </tr>
              </thead>
              <tbody>
                {bedDemand.map((ward) => (
                  <tr key={ward.wardCode}>
                    <td>
                      <strong>{ward.wardCode}</strong>
                      <div className="text-muted" style={{ fontSize: '0.75rem' }}>{ward.wardName}</div>
                    </td>
                    <td>{ward.currentOccupied}</td>
                    <td>
                      <span className="text-available">{ward.currentAvailable}</span>
                    </td>
                    <td>+{ward.predictedAdmissionsNext24h}</td>
                    <td>
                      <strong className={ward.netBedBalance <= 0 ? 'text-danger' : 'text-available'}>
                        {ward.netBedBalance > 0 ? `+${ward.netBedBalance}` : ward.netBedBalance}
                      </strong>
                    </td>
                    <td>
                      <span className={`badge ${getSeverityBadgeClass(ward.shortageRiskLevel)}`}>
                        {ward.shortageRiskLevel}
                      </span>
                    </td>
                  </tr>
                ))}
                {bedDemand.length === 0 && (
                  <tr>
                    <td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                      No ward projections available
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Real-time Flow Bottlenecks & Anomalies */}
        <div className="card">
          <div className="card-header">
            <div className="card-title">
              <AlertTriangle size={18} />
              <span>Real-Time Flow Anomalies & Alerts</span>
            </div>
            <span className="badge badge-occupied">{anomalies.length} Flagged</span>
          </div>
          <div className="anomaly-list">
            {anomalies.map((anom) => (
              <div key={anom.id} className={`anomaly-item anomaly-${anom.severity.toLowerCase()}`}>
                <div className="anomaly-header">
                  <span className="anomaly-type">{anom.anomalyType.replace(/_/g, ' ')}</span>
                  <span className="anomaly-patient">{anom.patientName} ({anom.visitNumber})</span>
                  <span className={`badge ${getSeverityBadgeClass(anom.severity)}`}>{anom.severity}</span>
                </div>
                <p className="anomaly-desc">{anom.description}</p>
              </div>
            ))}
            {anomalies.length === 0 && (
              <div className="no-anomalies">
                <CheckCircle2 size={28} className="text-available" />
                <p>No acute queue bottlenecks or extended dwell anomalies detected.</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Immediate Triage Queue Snapshot */}
      <div className="card" style={{ marginTop: '1.5rem' }}>
        <div className="card-header">
          <div className="card-title">
            <Activity size={18} />
            <span>Active High-Acuity Emergency Arrivals</span>
          </div>
          <span className="text-muted" style={{ fontSize: '0.82rem' }}>Sorted by ESI triage priority</span>
        </div>
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>ESI</th>
                <th>Patient</th>
                <th>MRN / Visit</th>
                <th>Chief Complaint</th>
                <th>Vitals (HR / BP / SpO2)</th>
                <th>Wait Time</th>
              </tr>
            </thead>
            <tbody>
              {triageQueue.slice(0, 5).map((item) => (
                <tr key={item.id}>
                  <td>
                    <span className={`badge badge-esi-${item.esiLevel}`}>
                      ESI {item.esiLevel}
                    </span>
                  </td>
                  <td><strong>{item.patientName}</strong></td>
                  <td className="mono">{item.mrn} &bull; {item.visitNumber}</td>
                  <td>{item.chiefComplaint}</td>
                  <td className="mono">
                    {item.heartRate} bpm &bull; {item.bloodPressureSys}/{item.bloodPressureDia} &bull; {item.o2Saturation}%
                  </td>
                  <td className="text-muted">
                    {new Date(item.triageTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </td>
                </tr>
              ))}
              {triageQueue.length === 0 && (
                <tr>
                  <td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                    No pending triage assessments in queue.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
