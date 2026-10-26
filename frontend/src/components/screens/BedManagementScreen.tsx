import React, { useState, useEffect } from 'react';
import {
  BedDouble,
  CheckCircle,
  AlertCircle,
  Sparkles,
  User,
  Clock,
  ArrowRight,
  Filter,
  RefreshCw,
  LogOut,
  ShieldAlert,
} from 'lucide-react';
import { bedApi } from '../../services/bedApi';
import { patientApi } from '../../services/patientApi';
import { Bed, BedAllocationDto, BedCapacitySummaryDto, BedStatus, TriageAssessmentResponseDto } from '../../types';
import './BedManagementScreen.css';

export const BedManagementScreen: React.FC = () => {
  const [beds, setBeds] = useState<Bed[]>([]);
  const [summary, setSummary] = useState<BedCapacitySummaryDto | null>(null);
  const [selectedWardId, setSelectedWardId] = useState<number | null>(null);
  const [selectedStatus, setSelectedStatus] = useState<BedStatus | ''>('');
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Admission Modal State
  const [admitBedTarget, setAdmitBedTarget] = useState<Bed | null>(null);
  const [waitingVisits, setWaitingVisits] = useState<TriageAssessmentResponseDto[]>([]);
  const [selectedVisitId, setSelectedVisitId] = useState<number | null>(null);
  const [admissionNotes, setAdmissionNotes] = useState('');

  const loadData = async () => {
    try {
      const [allBeds, capSummary, queue] = await Promise.all([
        bedApi.getBeds(selectedWardId || undefined, (selectedStatus as BedStatus) || undefined),
        bedApi.getSummary(),
        patientApi.getTriageQueue().catch(() => []),
      ]);
      setBeds(allBeds || []);
      setSummary(capSummary);
      setWaitingVisits(queue || []);
    } catch (err: any) {
      console.error('Failed to load bed management data', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [selectedWardId, selectedStatus]);

  const handleOpenAdmitModal = (bed: Bed) => {
    setAdmitBedTarget(bed);
    setSelectedVisitId(waitingVisits.length > 0 ? waitingVisits[0].visitId : null);
    setAdmissionNotes('Direct ED triage admission to unit.');
    setFeedback(null);
  };

  const handleAdmitSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!admitBedTarget || !selectedVisitId) return;

    setActionLoading(true);
    try {
      const allocDto: BedAllocationDto = {
        bedId: admitBedTarget.id,
        visitId: selectedVisitId,
        notes: admissionNotes,
      };
      await bedApi.admitPatient(allocDto);
      setFeedback({
        type: 'success',
        text: `Patient assigned to Bed ${admitBedTarget.bedNumber} (${admitBedTarget.wardCode}) successfully.`,
      });
      setAdmitBedTarget(null);
      loadData();
    } catch (err: any) {
      setFeedback({
        type: 'error',
        text: err.message || 'Bed admission conflict. Please refresh and retry.',
      });
    } finally {
      setActionLoading(false);
    }
  };

  const handleDischarge = async (bed: Bed) => {
    if (!window.confirm(`Discharge patient from Bed ${bed.bedNumber}? Bed will transition to CLEANING status.`)) {
      return;
    }
    setActionLoading(true);
    try {
      await bedApi.dischargeBed(bed.id);
      setFeedback({
        type: 'success',
        text: `Bed ${bed.bedNumber} discharged. Terminal sanitization protocol initiated.`,
      });
      loadData();
    } catch (err: any) {
      setFeedback({ type: 'error', text: err.message || 'Discharge failed.' });
    } finally {
      setActionLoading(false);
    }
  };

  const handleMarkCleaned = async (bed: Bed) => {
    setActionLoading(true);
    try {
      await bedApi.markCleaned(bed.id);
      setFeedback({
        type: 'success',
        text: `Bed ${bed.bedNumber} sanitization certified. Now AVAILABLE for admissions.`,
      });
      loadData();
    } catch (err: any) {
      setFeedback({ type: 'error', text: err.message || 'Cleaning verification failed.' });
    } finally {
      setActionLoading(false);
    }
  };

  const getStatusBadge = (status: BedStatus) => {
    switch (status) {
      case 'AVAILABLE':
        return <span className="badge badge-available">AVAILABLE</span>;
      case 'OCCUPIED':
        return <span className="badge badge-occupied">OCCUPIED</span>;
      case 'CLEANING':
        return <span className="badge badge-cleaning">CLEANING</span>;
      case 'MAINTENANCE':
        return <span className="badge badge-maintenance">MAINTENANCE</span>;
    }
  };

  return (
    <div className="beds-screen animate-fade-in">
      <div className="screen-header">
        <div>
          <h1>Inpatient & Acute Ward Bed Management</h1>
          <p className="screen-subtitle">
            Real-time atomic bed occupancy tracking, sanitization workflows, and telemetry allocations
          </p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={() => loadData()}>
          <RefreshCw size={14} />
          <span>Refresh Grid</span>
        </button>
      </div>

      {feedback && (
        <div className={`screen-alert ${feedback.type === 'success' ? 'alert-success' : 'alert-danger'}`}>
          {feedback.type === 'success' ? <CheckCircle size={18} /> : <AlertCircle size={18} />}
          <span>{feedback.text}</span>
        </div>
      )}

      {/* Ward Capacity Summary Cards */}
      {summary && (
        <div className="ward-stats-grid">
          {(summary.wardSummaries || []).map((w) => (
            <div
              key={w.wardId}
              className={`ward-kpi-card ${selectedWardId === w.wardId ? 'active' : ''}`}
              onClick={() => setSelectedWardId(selectedWardId === w.wardId ? null : w.wardId)}
            >
              <div className="ward-kpi-top">
                <span className="ward-code-title">{w.wardCode}</span>
                <span className="badge badge-ai">{w.department}</span>
              </div>
              <div className="ward-name-label">{w.wardName}</div>
              
              <div className="ward-occupancy-bar">
                <div
                  className="ward-occ-fill"
                  style={{ width: `${w.occupancyRate ?? 0}%` }}
                ></div>
              </div>

              <div className="ward-kpi-breakdown">
                <span>{w.occupancyRate ?? 0}% Full</span>
                <span className="text-available">{w.availableBeds ?? 0} free</span>
                <span className="text-danger">{w.occupiedBeds ?? 0} occupied</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Filters Bar */}
      <div className="bed-filters-bar card">
        <div className="filter-group">
          <Filter size={16} />
          <span>Status Filter:</span>
          {(['', 'AVAILABLE', 'OCCUPIED', 'CLEANING', 'MAINTENANCE'] as const).map((st) => (
            <button
              key={st || 'ALL'}
              className={`filter-btn ${selectedStatus === st ? 'active' : ''}`}
              onClick={() => setSelectedStatus(st)}
            >
              {st || 'All Beds'}
            </button>
          ))}
        </div>
        <div className="selected-ward-indicator">
          {selectedWardId ? (
            <span>Filtering by Ward ID #{selectedWardId} &bull; <button className="clear-link" onClick={() => setSelectedWardId(null)}>Clear</button></span>
          ) : (
            <span>Showing all hospital wards ({beds.length} beds)</span>
          )}
        </div>
      </div>

      {/* Interactive Bed Grid */}
      <div className="bed-grid">
        {(beds || []).map((bed) => {
          const isAvailable = bed.status === 'AVAILABLE';
          const isOccupied = bed.status === 'OCCUPIED';
          const isCleaning = bed.status === 'CLEANING';

          return (
            <div key={bed.id} className={`bed-card bed-${bed.status.toLowerCase()}`}>
              <div className="bed-card-header">
                <div className="bed-num-title">
                  <BedDouble size={20} />
                  <span>{bed.bedNumber}</span>
                </div>
                {getStatusBadge(bed.status)}
              </div>

              <div className="bed-meta-row">
                <span className="bed-ward-tag">{bed.wardCode}</span>
                <span className="bed-type-tag">{bed.bedType.replace('_', ' ')}</span>
              </div>

              {isOccupied && (
                <div className="bed-patient-info">
                  <div className="patient-name">
                    <User size={14} />
                    <span>{bed.currentPatientName || 'Assigned Patient'}</span>
                  </div>
                  <div className="visit-num mono">
                    {bed.currentVisitNumber || `Visit #${bed.currentVisitId}`}
                  </div>
                  {bed.occupiedSince && (
                    <div className="dwell-time">
                      <Clock size={12} />
                      <span>Since {new Date(bed.occupiedSince).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                    </div>
                  )}
                </div>
              )}

              {isAvailable && (
                <div className="bed-available-placeholder">
                  <span>Sanitized & Ready</span>
                </div>
              )}

              {isCleaning && (
                <div className="bed-cleaning-placeholder">
                  <Sparkles size={16} className="text-cleaning" />
                  <span>Sanitization & Disinfection in progress</span>
                </div>
              )}

              <div className="bed-card-actions">
                {isAvailable && (
                  <button
                    className="btn btn-primary btn-sm bed-action-btn"
                    onClick={() => handleOpenAdmitModal(bed)}
                    disabled={actionLoading}
                  >
                    <span>Admit Patient</span>
                    <ArrowRight size={14} />
                  </button>
                )}
                {isOccupied && (
                  <button
                    className="btn btn-secondary btn-sm bed-action-btn"
                    onClick={() => handleDischarge(bed)}
                    disabled={actionLoading}
                  >
                    <LogOut size={14} />
                    <span>Discharge</span>
                  </button>
                )}
                {isCleaning && (
                  <button
                    className="btn btn-secondary btn-sm bed-action-btn"
                    onClick={() => handleMarkCleaned(bed)}
                    disabled={actionLoading}
                  >
                    <CheckCircle size={14} />
                    <span>Mark Sanitized</span>
                  </button>
                )}
              </div>
            </div>
          );
        })}
        {(!beds || beds.length === 0) && (
          <div className="card" style={{ gridColumn: '1 / -1', padding: '2.5rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
            <BedDouble size={36} style={{ margin: '0 auto 0.75rem', opacity: 0.5 }} />
            <p style={{ fontWeight: 600, fontSize: '1rem', color: 'var(--text-main)', marginBottom: '0.25rem' }}>No beds found matching the selected filter</p>
            <p style={{ fontSize: '0.85rem', marginBottom: '1.25rem' }}>Try clearing the status or ward filter to view all beds.</p>
            <button className="btn btn-secondary btn-sm" onClick={() => { setSelectedWardId(null); setSelectedStatus(''); }}>
              Reset Filters
            </button>
          </div>
        )}
      </div>

      {/* Patient Admission Modal */}
      {admitBedTarget && (
        <div className="modal-backdrop">
          <div className="modal-dialog">
            <div className="modal-header">
              <h3>Admit Patient to Bed {admitBedTarget.bedNumber}</h3>
              <button className="btn btn-secondary btn-sm" onClick={() => setAdmitBedTarget(null)}>
                &times;
              </button>
            </div>

            <form onSubmit={handleAdmitSubmit}>
              <div className="modal-body">
                <div className="admission-target-banner">
                  <BedDouble size={18} />
                  <span>
                    Target Unit: <strong>{admitBedTarget.wardName} ({admitBedTarget.bedNumber})</strong> &bull; Type: {admitBedTarget.bedType}
                  </span>
                </div>

                <div className="form-group">
                  <label className="form-label">Select Patient from Triage Wait Queue *</label>
                  {waitingVisits.length > 0 ? (
                    <select
                      value={selectedVisitId || ''}
                      onChange={(e) => setSelectedVisitId(parseInt(e.target.value))}
                      required
                    >
                      {waitingVisits.map((v) => (
                        <option key={v.visitId} value={v.visitId}>
                          ESI {v.esiLevel} &bull; {v.patientName} ({v.mrn}) - {v.chiefComplaint.substring(0, 45)}...
                        </option>
                      ))}
                    </select>
                  ) : (
                    <div className="empty-warning">
                      <AlertCircle size={16} />
                      <span>No patients currently waiting in triage queue. Register an intake first.</span>
                    </div>
                  )}
                </div>

                <div className="form-group">
                  <label className="form-label">Admission Clinical Notes</label>
                  <textarea
                    rows={3}
                    placeholder="Document admitting physician, reason for bed tier, continuous telemetry orders..."
                    value={admissionNotes}
                    onChange={(e) => setAdmissionNotes(e.target.value)}
                  />
                </div>
              </div>

              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setAdmitBedTarget(null)}>
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={actionLoading || !selectedVisitId}
                >
                  {actionLoading ? 'Allocating Bed...' : 'Confirm Atomic Admission'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
