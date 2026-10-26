import React, { useState, useEffect } from 'react';
import {
  UserPlus,
  Activity,
  AlertCircle,
  CheckCircle,
  Search,
  ChevronRight,
} from 'lucide-react';
import { patientApi } from '../../services/patientApi';
import { queryClient } from '../../services/queryCache';
import {
  PatientRegistrationDto,
  TriageAssessmentResponseDto,
  VisitCreateDto,
} from '../../types';
import { TriageAssessmentModal } from '../../features/registration-triage/TriageAssessmentModal';
import './PatientTriageScreen.css';

export const PatientTriageScreen: React.FC = () => {
  const [activeView, setActiveView] = useState<'queue' | 'register'>('queue');
  const [queue, setQueue] = useState<TriageAssessmentResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // New Patient Form State
  const [regForm, setRegForm] = useState<PatientRegistrationDto>({
    firstName: '',
    lastName: '',
    dateOfBirth: '1985-06-15',
    gender: 'MALE',
    phone: '555-0123',
    emergencyContact: 'Next of Kin (555-9999)',
  });
  const [chiefComplaint, setChiefComplaint] = useState('');
  const [isSubmittingReg, setIsSubmittingReg] = useState(false);

  // Triage Assessment Modal State
  const [selectedVisitId, setSelectedVisitId] = useState<number | null>(null);
  const [selectedVisitTitle, setSelectedVisitTitle] = useState<string>('');
  const [initialAcuity, setInitialAcuity] = useState<number>(3);
  const [initialNotes, setInitialNotes] = useState<string>('');

  const loadTriageQueue = async () => {
    try {
      const data = await patientApi.getTriageQueue();
      setQueue(Array.isArray(data) ? data : []);
    } catch (err: any) {
      console.error('Failed to load triage queue', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTriageQueue();
    // Subscribe to cross-module triage-queue cache invalidations
    const unsub = queryClient.subscribe('triage-queue', loadTriageQueue);
    return () => unsub();
  }, []);

  const handleRegisterPatient = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmittingReg(true);
    setMessage(null);
    try {
      // 1. Register Patient (201 Created)
      const patient = await patientApi.registerPatient(regForm);
      // 2. Create Emergency Encounter Visit (201 Created)
      const visitDto: VisitCreateDto = {
        patientId: patient.id,
        visitType: 'EMERGENCY',
        chiefComplaint: chiefComplaint || 'Acute presentation to Emergency Department',
      };
      const visit = await patientApi.createVisit(patient.id, visitDto);

      setMessage({
        type: 'success',
        text: `Patient ${patient.firstName} ${patient.lastName} (MRN: ${patient.mrn}) registered under encounter ${visit.visitNumber}. Active queue refreshed.`,
      });

      // Prepare triage assessment modal for this new patient
      setSelectedVisitId(visit.id);
      setSelectedVisitTitle(`${patient.firstName} ${patient.lastName} (${visit.visitNumber})`);
      setInitialAcuity(3);
      setInitialNotes(chiefComplaint ? `Intake complaint: ${chiefComplaint}` : '');

      // Reset reg form
      setRegForm({
        firstName: '',
        lastName: '',
        dateOfBirth: '1990-01-01',
        gender: 'MALE',
        phone: '',
        emergencyContact: '',
      });
      setChiefComplaint('');
      setActiveView('queue');

      // Immediately invalidate cache and re-fetch triage queue
      await loadTriageQueue();
      queryClient.invalidateQueries({ queryKey: ['triage-queue'] });
      queryClient.invalidateQueries({ queryKey: ['flow-anomalies'] });
      queryClient.invalidateQueries({ queryKey: ['command-center-metrics'] });
    } catch (err: any) {
      setMessage({ type: 'error', text: err.message || 'Failed to register patient.' });
    } finally {
      setIsSubmittingReg(false);
    }
  };

  const handleOpenTriage = (visitId: number, patientName: string, visitNumber: string, complaint: string, esiLevel?: number) => {
    setSelectedVisitId(visitId);
    setSelectedVisitTitle(`${patientName} (${visitNumber})`);
    setInitialAcuity(esiLevel || 3);
    setInitialNotes(complaint ? `Initial assessment for: ${complaint}` : '');
  };

  // Sort queue by Acuity (ESI ascending) and creation timestamp (descending) so new ESI 1 appears as row #1
  const sortedQueue = [...queue].sort((a, b) => {
    const esiA = a.esiLevel ?? 5;
    const esiB = b.esiLevel ?? 5;
    if (esiA !== esiB) {
      return esiA - esiB; // ESI 1 comes before ESI 2, 3, 4, 5
    }
    const timeA = new Date(a.triageTime || 0).getTime();
    const timeB = new Date(b.triageTime || 0).getTime();
    return timeB - timeA; // newest first
  });

  const filteredQueue = sortedQueue.filter(
    (item) =>
      item.patientName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.mrn.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.chiefComplaint.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="triage-screen animate-fade-in">
      {/* Screen Sub-header */}
      <div className="screen-header">
        <div>
          <h1>Emergency Intake & Clinical Triage</h1>
          <p className="screen-subtitle">
            Rapid patient demographic intake and Emergency Severity Index (ESI 1-5) assessment
          </p>
        </div>
        <div className="tab-pill-group">
          <button
            className={`tab-pill ${activeView === 'queue' ? 'active' : ''}`}
            onClick={() => setActiveView('queue')}
          >
            <Activity size={16} />
            <span>Live Triage Queue ({queue.length})</span>
          </button>
          <button
            className={`tab-pill ${activeView === 'register' ? 'active' : ''}`}
            onClick={() => setActiveView('register')}
          >
            <UserPlus size={16} />
            <span>New Patient Registration</span>
          </button>
        </div>
      </div>

      {/* Alert Notification */}
      {message && (
        <div className={`screen-alert ${message.type === 'success' ? 'alert-success' : 'alert-danger'}`}>
          {message.type === 'success' ? <CheckCircle size={18} /> : <AlertCircle size={18} />}
          <span>{message.text}</span>
        </div>
      )}

      {/* View: Live Triage Queue */}
      {activeView === 'queue' && (
        <div className="card">
          <div className="queue-filter-bar">
            <div className="search-box">
              <Search size={16} />
              <input
                type="text"
                placeholder="Search triage queue by patient name, MRN, or complaint..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <div className="esi-legend">
              <span className="badge badge-esi-1">ESI 1 Resuscitation</span>
              <span className="badge badge-esi-2">ESI 2 Emergent</span>
              <span className="badge badge-esi-3">ESI 3 Urgent</span>
              <span className="badge badge-esi-4">ESI 4 Less Urgent</span>
              <span className="badge badge-esi-5">ESI 5 Non-Urgent</span>
            </div>
          </div>

          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Acuity</th>
                  <th>Patient Details</th>
                  <th>Encounter / MRN</th>
                  <th>Chief Complaint</th>
                  <th>Vitals Baseline</th>
                  <th>Triaged By</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {filteredQueue.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <span className={`badge badge-esi-${item.esiLevel}`}>
                        ESI {item.esiLevel}
                      </span>
                    </td>
                    <td>
                      <strong>{item.patientName}</strong>
                    </td>
                    <td className="mono">
                      <div>{item.visitNumber}</div>
                      <div className="text-muted" style={{ fontSize: '0.75rem' }}>{item.mrn}</div>
                    </td>
                    <td style={{ maxWidth: '280px' }}>
                      <div className="complaint-text">{item.chiefComplaint}</div>
                    </td>
                    <td className="mono" style={{ fontSize: '0.8rem' }}>
                      <div>BP: {item.bloodPressureSys}/{item.bloodPressureDia} mmHg &bull; HR: {item.heartRate} bpm</div>
                      <div className="text-muted">SpO2: {item.o2Saturation}% &bull; RR: {item.respiratoryRate} &bull; Temp: {item.temperatureCelsius}&deg;C</div>
                    </td>
                    <td>
                      <span className="text-secondary">{item.triagedByName}</span>
                      <div className="text-muted" style={{ fontSize: '0.75rem' }}>
                        {new Date(item.triageTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </div>
                    </td>
                    <td>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleOpenTriage(item.visitId, item.patientName, item.visitNumber, item.chiefComplaint, item.esiLevel)}
                      >
                        <span>Re-Triage</span>
                        <ChevronRight size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
                {filteredQueue.length === 0 && !loading && (
                  <tr>
                    <td colSpan={7} style={{ textAlign: 'center', padding: '2.5rem', color: 'var(--text-muted)' }}>
                      No patients found matching current filter.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* View: New Patient Registration */}
      {activeView === 'register' && (
        <div className="card registration-card">
          <div className="card-header">
            <div className="card-title">
              <UserPlus size={18} />
              <span>Intake Demographic Intake & Emergency Visit Generation</span>
            </div>
            <span className="text-muted" style={{ fontSize: '0.8rem' }}>Generates auto-sequenced MRN & Encounter</span>
          </div>

          <form onSubmit={handleRegisterPatient} className="reg-form">
            <div className="form-row">
              <div className="form-group">
                <label className="form-label">First Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Liam"
                  value={regForm.firstName}
                  onChange={(e) => setRegForm({ ...regForm, firstName: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Last Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Vance"
                  value={regForm.lastName}
                  onChange={(e) => setRegForm({ ...regForm, lastName: e.target.value })}
                />
              </div>
            </div>

            <div className="form-row">
              <div className="form-group">
                <label className="form-label">Date of Birth *</label>
                <input
                  type="date"
                  required
                  value={regForm.dateOfBirth}
                  onChange={(e) => setRegForm({ ...regForm, dateOfBirth: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Gender *</label>
                <select
                  value={regForm.gender}
                  onChange={(e) => setRegForm({ ...regForm, gender: e.target.value as any })}
                >
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
                  <option value="OTHER">Other / Non-Binary</option>
                </select>
              </div>
            </div>

            <div className="form-row">
              <div className="form-group">
                <label className="form-label">Contact Phone</label>
                <input
                  type="text"
                  placeholder="555-0100"
                  value={regForm.phone}
                  onChange={(e) => setRegForm({ ...regForm, phone: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Emergency Contact Info</label>
                <input
                  type="text"
                  placeholder="Spouse / Parent (Phone number)"
                  value={regForm.emergencyContact}
                  onChange={(e) => setRegForm({ ...regForm, emergencyContact: e.target.value })}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Emergency Chief Complaint *</label>
              <textarea
                rows={3}
                required
                placeholder="Describe presenting symptoms, onset, and immediate pain complaints..."
                value={chiefComplaint}
                onChange={(e) => setChiefComplaint(e.target.value)}
              />
            </div>

            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={isSubmittingReg}>
                {isSubmittingReg ? 'Generating Encounter...' : 'Register Patient & Create Visit'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Triage Assessment Modal */}
      <TriageAssessmentModal
        key={selectedVisitId ?? 'closed'}
        isOpen={selectedVisitId !== null}
        visitId={selectedVisitId}
        visitTitle={selectedVisitTitle}
        initialAcuity={initialAcuity}
        initialNotes={initialNotes}
        onClose={() => setSelectedVisitId(null)}
        onSuccess={(_savedTriage, successMessage) => {
          setMessage({
            type: 'success',
            text: successMessage,
          });
          loadTriageQueue();
          queryClient.invalidateQueries({ queryKey: ['triage-queue'] });
          queryClient.invalidateQueries({ queryKey: ['flow-anomalies'] });
          queryClient.invalidateQueries({ queryKey: ['command-center-metrics'] });
        }}
      />
    </div>
  );
};
