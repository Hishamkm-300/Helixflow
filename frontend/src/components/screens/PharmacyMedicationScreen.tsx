import React, { useState, useEffect } from 'react';
import {
  Pill,
  ShieldAlert,
  AlertTriangle,
  CheckCircle,
  Search,
  Plus,
  RefreshCw,
  Clock,
  User,
  ShieldCheck,
  AlertCircle,
  Activity,
  FileCheck,
} from 'lucide-react';
import { medicationApi } from '../../services/medicationApi';
import { clinicalApi } from '../../services/clinicalApi';
import {
  ActiveEncounterDto,
  MedicationDto,
  MedicationOrderDto,
  MedicationPrescribeRequest,
  SafetyCheckResultDto,
} from '../../types';
import './PharmacyMedicationScreen.css';

export const PharmacyMedicationScreen: React.FC = () => {
  const [formulary, setFormulary] = useState<MedicationDto[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Active visit and orders
  const [activeVisits, setActiveVisits] = useState<ActiveEncounterDto[]>([]);
  const [selectedVisitId, setSelectedVisitId] = useState<number>(1);
  const [activeOrders, setActiveOrders] = useState<MedicationOrderDto[]>([]);
  const [isRefreshingVisits, setIsRefreshingVisits] = useState(false);

  // Prescribe form & live safety check
  const [selectedMedId, setSelectedMedId] = useState<number>(1);
  const [dose, setDose] = useState('81');
  const [route, setRoute] = useState('ORAL');
  const [frequency, setFrequency] = useState('ONCE_DAILY');
  const [safetyResult, setSafetyResult] = useState<SafetyCheckResultDto | null>(null);
  const [checkingSafety, setCheckingSafety] = useState(false);

  // Override Modal
  const [isOverrideModalOpen, setIsOverrideModalOpen] = useState(false);
  const [overrideReason, setOverrideReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Discontinue Order Modal
  const [orderToDiscontinue, setOrderToDiscontinue] = useState<MedicationOrderDto | null>(null);
  const [discontinueReason, setDiscontinueReason] = useState('Dosing error');
  const [isDiscontinuing, setIsDiscontinuing] = useState(false);

  const selectedDrug = formulary.find((m) => m.id === selectedMedId);

  // Sync default dose when selected drug changes
  useEffect(() => {
    if (selectedDrug) {
      const match = selectedDrug.strength?.match(/([0-9]+(?:\.[0-9]+)?)/);
      if (match) {
        setDose(match[1]);
      } else if (selectedDrug.maxSingleDoseMg) {
        setDose(String(Math.min(selectedDrug.maxSingleDoseMg, 100)));
      }
    }
  }, [selectedMedId, formulary]);

  const numDose = parseFloat(dose);
  const isDosageExceeded = !isNaN(numDose) && selectedDrug?.maxSingleDoseMg != null && numDose > selectedDrug.maxSingleDoseMg;

  const sortEncountersDesc = (list: ActiveEncounterDto[]): ActiveEncounterDto[] => {
    return [...list].sort((a, b) => {
      const timeA = a.arrivalTime ? new Date(a.arrivalTime).getTime() : 0;
      const timeB = b.arrivalTime ? new Date(b.arrivalTime).getTime() : 0;
      return timeB - timeA;
    });
  };

  const loadEncounters = async () => {
    setIsRefreshingVisits(true);
    try {
      const encounters = await clinicalApi.getActiveEncounters();
      const sorted = sortEncountersDesc(encounters || []);
      setActiveVisits(sorted);
      if (sorted.length > 0) {
        setSelectedVisitId((prev) => {
          const exists = sorted.some((e) => e.visitId === prev);
          return exists ? prev : sorted[0].visitId;
        });
      }
    } catch (err) {
      console.error('Failed to load active encounters for pharmacy selector', err);
    } finally {
      setIsRefreshingVisits(false);
    }
  };

  const loadInitialData = async () => {
    try {
      const [meds, encounters] = await Promise.all([
        medicationApi.searchMedications(),
        clinicalApi.getActiveEncounters().catch(() => []),
      ]);
      setFormulary(meds);
      const sorted = sortEncountersDesc(encounters || []);
      setActiveVisits(sorted);
      if (sorted.length > 0) {
        setSelectedVisitId(sorted[0].visitId);
      }
    } catch (err) {
      console.error('Failed to load pharmacy data', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadInitialData();
  }, []);

  const loadVisitOrders = async (vId: number) => {
    try {
      const orders = await medicationApi.getActiveOrdersForVisit(vId);
      setActiveOrders(orders);
    } catch (err) {
      console.error('Failed to load visit medication orders', err);
    }
  };

  useEffect(() => {
    if (selectedVisitId) {
      loadVisitOrders(selectedVisitId);
    }
  }, [selectedVisitId]);

  // Live safety pre-check whenever selected medication or patient visit changes
  useEffect(() => {
    if (!selectedMedId || !selectedVisitId) return;

    const runSafetyCheck = async () => {
      setCheckingSafety(true);
      try {
        const result = await medicationApi.evaluateSafety({
          visitId: selectedVisitId,
          proposedMedicationIds: [selectedMedId],
        });
        setSafetyResult(result);
      } catch (err) {
        console.error('Safety check failed', err);
      } finally {
        setCheckingSafety(false);
      }
    };

    const timer = setTimeout(runSafetyCheck, 250);
    return () => clearTimeout(timer);
  }, [selectedMedId, selectedVisitId]);

  const handlePrescribeClick = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedMedId || !selectedVisitId) return;

    if (isDosageExceeded && selectedDrug?.maxSingleDoseMg) {
      setFeedback({
        type: 'error',
        text: `Safe threshold exceeded: maximum allowable dose is ${selectedDrug.maxSingleDoseMg} ${selectedDrug.unit || 'mg'}`,
      });
      return;
    }

    // If hard-blocked by contraindicated pair, trigger override modal
    if (safetyResult?.hardBlocked) {
      setIsOverrideModalOpen(true);
      return;
    }

    await submitPrescription(false, undefined);
  };

  const submitPrescription = async (overridden: boolean, reason?: string) => {
    if (isDosageExceeded && selectedDrug?.maxSingleDoseMg) {
      setFeedback({
        type: 'error',
        text: `Safe threshold exceeded: maximum allowable dose is ${selectedDrug.maxSingleDoseMg} ${selectedDrug.unit || 'mg'}`,
      });
      return;
    }

    setIsSubmitting(true);
    setFeedback(null);
    try {
      const numericDose = parseFloat(dose);
      const unit = selectedDrug?.unit || 'mg';
      const formattedDose = !isNaN(numericDose) ? `${numericDose}${unit}` : dose;

      const request: MedicationPrescribeRequest = {
        visitId: selectedVisitId,
        medicationId: selectedMedId,
        dose: formattedDose,
        numericDose: !isNaN(numericDose) ? numericDose : undefined,
        route,
        frequency,
        interactionOverridden: overridden,
        overrideReason: reason,
      };

      const newOrder = await medicationApi.prescribeMedication(request);
      setFeedback({
        type: 'success',
        text: `Prescription for ${newOrder.medicationName || newOrder.genericName} (${newOrder.dose}) recorded successfully${
          overridden ? ' with clinical override documentation.' : '.'
        }`,
      });

      setIsOverrideModalOpen(false);
      setOverrideReason('');
      await loadVisitOrders(selectedVisitId);

      // Refresh safety check
      if (selectedMedId && selectedVisitId) {
        const res = await medicationApi.evaluateSafety({
          visitId: selectedVisitId,
          proposedMedicationIds: [selectedMedId],
        });
        setSafetyResult(res);
      }
    } catch (err: any) {
      setFeedback({
        type: 'error',
        text: err.message || 'Prescription blocked by medication safety engine.',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDiscontinueOrder = async (orderId: number, reason: string) => {
    setIsDiscontinuing(true);
    setFeedback(null);
    try {
      const updated = await medicationApi.discontinueOrder(orderId, reason);
      setFeedback({
        type: 'success',
        text: `Order #${orderId} (${updated.medicationName || updated.genericName || 'Medication'}) discontinued successfully. Reason: "${reason}".`,
      });
      setOrderToDiscontinue(null);
      setDiscontinueReason('Dosing error');

      // Refresh orders immediately
      await loadVisitOrders(selectedVisitId);

      // Refresh safety check immediately to clear discontinued medication from active interactions
      if (selectedMedId && selectedVisitId) {
        const res = await medicationApi.evaluateSafety({
          visitId: selectedVisitId,
          proposedMedicationIds: [selectedMedId],
        });
        setSafetyResult(res);
      }
    } catch (err: any) {
      setFeedback({
        type: 'error',
        text: err.message || 'Failed to discontinue medication order.',
      });
    } finally {
      setIsDiscontinuing(false);
    }
  };

  const filteredFormulary = formulary.filter(
    (m) =>
      m.genericName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      m.brandName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      m.code.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const selectedPatient = activeVisits.find((v) => v.visitId === selectedVisitId);

  return (
    <div className="pharmacy-screen animate-fade-in">
      <div className="screen-header">
        <div>
          <h1>Formulary & Deterministic Medication Safety</h1>
          <p className="screen-subtitle">
            Pairwise drug-drug interaction alerts, hard-stop contraindication blocks, and clinical override audits
          </p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={() => loadInitialData()}>
          <RefreshCw size={14} />
          <span>Refresh Formulary</span>
        </button>
      </div>

      {feedback && (
        <div className={`screen-alert ${feedback.type === 'success' ? 'alert-success' : 'alert-danger'}`}>
          {feedback.type === 'success' ? <CheckCircle size={18} /> : <AlertCircle size={18} />}
          <span>{feedback.text}</span>
        </div>
      )}

      {/* Main Grid: Order Entry & Safety Scanner */}
      <div className="pharmacy-main-grid">
        {/* Left Column: Medication Order Entry & Safety Scanner */}
        <div className="card">
          <div className="card-header">
            <div className="card-title">
              <Pill size={18} />
              <span>Prescription Order Entry & Interaction Scanner</span>
            </div>
            <span className="badge badge-ai">O(N²) Pairwise Engine</span>
          </div>

          <form onSubmit={handlePrescribeClick} className="order-form">
            <div className="form-group">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
                <label className="form-label" style={{ marginBottom: 0 }}>Select Patient Encounter *</label>
                <button
                  type="button"
                  className="btn btn-secondary btn-xs"
                  style={{ padding: '0.2rem 0.55rem', fontSize: '0.75rem', display: 'inline-flex', alignItems: 'center', gap: '0.3rem' }}
                  onClick={loadEncounters}
                  disabled={isRefreshingVisits}
                  title="Refresh active patient encounters"
                  aria-label="Refresh patient encounters"
                >
                  <RefreshCw size={12} className={isRefreshingVisits ? 'animate-spin' : ''} />
                  <span>↻ Refresh</span>
                </button>
              </div>
              {activeVisits.length > 0 ? (
                <select
                  value={selectedVisitId}
                  onChange={(e) => setSelectedVisitId(parseInt(e.target.value))}
                  required
                >
                  {activeVisits.map((encounter) => {
                    const patientName =
                      encounter.patientName ||
                      (encounter.patient
                        ? `${encounter.patient.firstName || ''} ${encounter.patient.lastName || ''}`.trim()
                        : `Patient #${encounter.patientId}`);
                    const esi = encounter.esiLevel || 'N/A';
                    return (
                      <option key={encounter.visitId} value={encounter.visitId}>
                        {patientName} • {encounter.visitNumber} • ESI {esi}
                      </option>
                    );
                  })}
                </select>
              ) : (
                <input type="text" disabled value="No active patient encounters loaded" />
              )}
            </div>

            <div className="form-group">
              <label className="form-label">Select Medication from Hospital Formulary *</label>
              <select
                value={selectedMedId}
                onChange={(e) => setSelectedMedId(parseInt(e.target.value))}
                required
              >
                {formulary.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.genericName} ({m.brandName}) &bull; {m.strength} [{m.dosageForm}] &bull; {m.code}
                  </option>
                ))}
              </select>
            </div>

            <div className="form-row">
              <div className="form-group">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.2rem' }}>
                  <label className="form-label" style={{ marginBottom: 0 }}>
                    Dose ({selectedDrug?.unit || 'mg'}) *
                  </label>
                  {selectedDrug?.maxSingleDoseMg && (
                    <span style={{ fontSize: '0.72rem', color: isDosageExceeded ? '#f87171' : 'var(--text-secondary)' }}>
                      Max: <strong>{selectedDrug.maxSingleDoseMg} {selectedDrug.unit || 'mg'}</strong>
                    </span>
                  )}
                </div>
                <input
                  id="medication-dose-input"
                  type="number"
                  min="0.1"
                  max={selectedDrug?.maxSingleDoseMg || undefined}
                  step="any"
                  required
                  placeholder={`e.g. ${selectedDrug?.maxSingleDoseMg || 10}`}
                  value={dose}
                  onChange={(e) => setDose(e.target.value)}
                  style={isDosageExceeded ? { borderColor: '#ef4444' } : undefined}
                />
                <div className={`dosage-limit-hint ${isDosageExceeded ? 'exceeded' : ''}`}>
                  {selectedDrug?.maxSingleDoseMg ? (
                    <>
                      {isDosageExceeded ? <AlertCircle size={12} /> : <ShieldCheck size={12} />}
                      <span>
                        {isDosageExceeded
                          ? `Safe threshold exceeded: maximum allowable dose is ${selectedDrug.maxSingleDoseMg} ${selectedDrug.unit || 'mg'}`
                          : `Single-dose threshold: ≤ ${selectedDrug.maxSingleDoseMg} ${selectedDrug.unit || 'mg'}`}
                      </span>
                    </>
                  ) : (
                    <span>Strength: {selectedDrug?.strength}</span>
                  )}
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Route *</label>
                <select value={route} onChange={(e) => setRoute(e.target.value)}>
                  <option value="ORAL">ORAL (PO)</option>
                  <option value="IV_INJECTION">IV INJECTION</option>
                  <option value="SUBLINGUAL">SUBLINGUAL</option>
                  <option value="TOPICAL">TOPICAL</option>
                  <option value="INHALATION">INHALATION</option>
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Frequency *</label>
                <select value={frequency} onChange={(e) => setFrequency(e.target.value)}>
                  <option value="ONCE_DAILY">Once Daily (QD)</option>
                  <option value="BID">Twice Daily (BID)</option>
                  <option value="TID">Three Times Daily (TID)</option>
                  <option value="PRN">As Needed (PRN)</option>
                  <option value="STAT">Immediately (STAT)</option>
                </select>
              </div>
            </div>

            {/* Live Safety Check Panel */}
            <div className="safety-check-panel">
              <div className="safety-panel-header">
                <ShieldCheck size={16} />
                <span>Deterministic Interaction Evaluation</span>
                {checkingSafety && <span className="text-muted" style={{ fontSize: '0.75rem' }}>Scanning...</span>}
              </div>

              {safetyResult && (
                <div className="safety-panel-body">
                  {safetyResult.safe ? (
                    <div className="safety-status safe">
                      <CheckCircle size={18} />
                      <span>{safetyResult.summaryMessage}</span>
                    </div>
                  ) : (
                    <div
                      className={`safety-status ${
                        safetyResult.hardBlocked ? 'hard-blocked' : 'warning'
                      }`}
                    >
                      {safetyResult.hardBlocked ? <ShieldAlert size={20} /> : <AlertTriangle size={20} />}
                      <div>
                        <strong>{safetyResult.summaryMessage}</strong>
                        {safetyResult.hardBlocked && (
                          <div style={{ fontSize: '0.78rem', marginTop: '0.25rem' }}>
                            Action Required: Hard-stop enabled. Clinician override justification is mandatory.
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                  {safetyResult.alerts.map((alert, idx) => (
                    <div key={idx} className="interaction-rule-card">
                      <div className="rule-card-header">
                        <span className="pair-names">
                          {alert.primaryMedName} + {alert.interactingMedName}
                        </span>
                        <span
                          className={`badge ${
                            alert.severity === 'SEVERE_CONTRAINDICATED'
                              ? 'badge-occupied'
                              : alert.severity === 'MODERATE'
                              ? 'badge-cleaning'
                              : 'badge-available'
                          }`}
                        >
                          {alert.severity}
                        </span>
                      </div>
                      <div className="rule-card-effect">{alert.clinicalEffect}</div>
                      <div className="rule-card-advice">
                        <strong>Protocol: </strong>{alert.managementAdvice}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <button
              type="submit"
              className={`btn ${safetyResult?.hardBlocked ? 'btn-danger' : 'btn-primary'} order-submit-btn`}
              disabled={isSubmitting}
            >
              <Plus size={16} />
              <span>
                {safetyResult?.hardBlocked
                  ? 'Request Override & Prescribe'
                  : 'Authorize & Prescribe Order'}
              </span>
            </button>
          </form>
        </div>

        {/* Right Column: Active Medications for Selected Patient */}
        <div className="card">
          <div className="card-header">
            <div className="card-title">
              <Clock size={18} />
              <span>Active Inpatient Medication Orders</span>
            </div>
            <span className="badge badge-ai">Encounter #{selectedVisitId}</span>
          </div>

          <div className="active-patient-badge">
            <User size={16} />
            <span>
              Patient: <strong>{selectedPatient?.patientName || (selectedPatient?.patient ? `${selectedPatient.patient.firstName} ${selectedPatient.patient.lastName}` : `Visit #${selectedVisitId}`)}</strong> ({selectedPatient?.mrn || 'MRN-SYN'})
            </span>
          </div>

          <div className="active-orders-list">
            {activeOrders.map((ord) => (
              <div key={ord.id} className={`active-order-item ${ord.status === 'DISCONTINUED' ? 'order-discontinued' : ''}`}>
                <div className="order-item-header">
                  <strong>{ord.medicationName || ord.genericName || `Medication #${ord.medicationId}`}</strong>
                  <span
                    className={`badge ${
                      ord.status === 'ACTIVE'
                        ? 'badge-available'
                        : ord.status === 'DISCONTINUED'
                        ? 'badge-discontinued'
                        : 'badge-cleaning'
                    }`}
                  >
                    {ord.status}
                  </span>
                </div>
                <div className="order-details mono">
                  {ord.dose} &bull; {ord.route} &bull; {ord.frequency}
                </div>
                <div className="order-doctor">
                  Prescribed by: {ord.prescribedByName || ord.prescribedByDoctorName || 'Doctor'} on {new Date(ord.createdAt).toLocaleDateString()}
                </div>
                {ord.status === 'DISCONTINUED' && ord.discontinueReason && (
                  <div className="order-doctor" style={{ marginTop: '0.35rem', color: '#94a3b8' }}>
                    <em>Discontinued: "{ord.discontinueReason}" {ord.discontinuedByDoctorName ? `by ${ord.discontinuedByDoctorName}` : ''}</em>
                  </div>
                )}
                {ord.interactionOverridden && (
                  <div className="order-override-tag">
                    <ShieldAlert size={12} />
                    <span>Overridden: "{ord.overrideReason}"</span>
                  </div>
                )}
                {ord.status === 'ACTIVE' && (
                  <div className="order-actions">
                    <button
                      type="button"
                      id={`discontinue-order-${ord.id}`}
                      className="btn btn-secondary btn-xs"
                      style={{
                        color: '#f87171',
                        borderColor: 'rgba(239, 68, 68, 0.4)',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '0.35rem',
                        padding: '0.25rem 0.6rem',
                        fontSize: '0.75rem',
                      }}
                      onClick={() => {
                        setOrderToDiscontinue(ord);
                        setDiscontinueReason('Dosing error');
                      }}
                      title="Discontinue / Stop medication order"
                    >
                      <AlertCircle size={12} />
                      <span>Discontinue / Stop Medication</span>
                    </button>
                  </div>
                )}
              </div>
            ))}
            {activeOrders.length === 0 && (
              <div className="empty-orders-placeholder">
                <CheckCircle size={28} className="text-available" />
                <p>No active medications currently administered to this encounter.</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Formulary Master Search Table */}
      <div className="card" style={{ marginTop: '1.5rem' }}>
        <div className="card-header">
          <div className="card-title">
            <FileCheck size={18} />
            <span>Hospital Formulary Master Directory</span>
          </div>
          <div className="search-box" style={{ minWidth: '260px' }}>
            <Search size={14} />
            <input
              type="text"
              placeholder="Search drug generic or brand name..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
        </div>

        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Rx Code</th>
                <th>Generic Name</th>
                <th>Brand Name</th>
                <th>Strength</th>
                <th>Max Single Dose</th>
                <th>Dosage Form</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {filteredFormulary.map((med) => (
                <tr key={med.id}>
                  <td className="mono text-cyan">{med.code}</td>
                  <td><strong>{med.genericName}</strong></td>
                  <td>{med.brandName}</td>
                  <td className="mono">{med.strength}</td>
                  <td className="mono" style={{ color: '#38bdf8' }}>
                    {med.maxSingleDoseMg ? `${med.maxSingleDoseMg} ${med.unit || 'mg'}` : '—'}
                  </td>
                  <td>
                    <span className="badge badge-ai">{med.dosageForm}</span>
                  </td>
                  <td>
                    <button
                      className="btn btn-secondary btn-sm"
                      onClick={() => {
                        setSelectedMedId(med.id);
                        window.scrollTo({ top: 0, behavior: 'smooth' });
                      }}
                    >
                      Select to Prescribe
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Hard-Stop Clinical Override Modal */}
      {isOverrideModalOpen && (
        <div className="modal-backdrop">
          <div className="modal-dialog">
            <div className="modal-header" style={{ borderBottomColor: 'rgba(239, 68, 68, 0.4)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#f87171' }}>
                <ShieldAlert size={20} />
                <h3 style={{ color: '#f87171' }}>Mandatory Interaction Override Justification</h3>
              </div>
              <button className="btn btn-secondary btn-sm" onClick={() => setIsOverrideModalOpen(false)}>
                &times;
              </button>
            </div>

            <div className="modal-body">
              <div className="safety-status hard-blocked" style={{ marginBottom: '1.25rem' }}>
                <AlertTriangle size={24} />
                <div>
                  <strong>HARD-STOP CONTRAINDICATION TRIGGERED</strong>
                  <p style={{ fontSize: '0.8rem', marginTop: '0.2rem' }}>
                    Prescribing this agent alongside active patient medications represents a severe medical hazard. To bypass this hard stop, attending physician clinical justification is required and will be logged.
                  </p>
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">
                  Clinical Override Justification & Monitoring Plan *
                </label>
                <textarea
                  rows={4}
                  required
                  placeholder="Document clinical rationale, specialist consultation, and intensive monitoring protocols (e.g. daily INR, telemetry, liver panel)..."
                  value={overrideReason}
                  onChange={(e) => setOverrideReason(e.target.value)}
                />
              </div>
            </div>

            <div className="modal-footer">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setIsOverrideModalOpen(false)}
              >
                Abort Prescription
              </button>
              <button
                type="button"
                className="btn btn-danger"
                disabled={!overrideReason.trim() || isSubmitting}
                onClick={() => submitPrescription(true, overrideReason)}
              >
                {isSubmitting ? 'Logging Override...' : 'Confirm Clinical Override & Sign Order'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Discontinue Medication Order Modal */}
      {orderToDiscontinue && (
        <div className="modal-backdrop">
          <div className="modal-dialog">
            <div className="modal-header" style={{ borderBottomColor: 'rgba(239, 68, 68, 0.4)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#f87171' }}>
                <AlertTriangle size={20} />
                <h3 style={{ color: '#f87171' }}>Discontinue Inpatient Medication Order</h3>
              </div>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => setOrderToDiscontinue(null)}
                disabled={isDiscontinuing}
              >
                &times;
              </button>
            </div>

            <div className="modal-body">
              <div className="discontinue-info-box">
                <div>
                  <strong>Order #{orderToDiscontinue.id}:</strong> {orderToDiscontinue.medicationName || orderToDiscontinue.genericName}
                </div>
                <div style={{ marginTop: '0.25rem', color: 'var(--text-secondary)', fontSize: '0.78rem' }}>
                  Dose: {orderToDiscontinue.dose} &bull; Route: {orderToDiscontinue.route} &bull; Frequency: {orderToDiscontinue.frequency}
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Select Standard Clinical Reason</label>
                <div className="reason-chips">
                  {[
                    'Dosing error',
                    'Course complete',
                    'Adverse reaction',
                    'Ineffective / Switched therapy',
                    'Patient request / contraindication',
                  ].map((r) => (
                    <button
                      key={r}
                      type="button"
                      className={`reason-chip-btn ${discontinueReason === r ? 'active' : ''}`}
                      onClick={() => setDiscontinueReason(r)}
                    >
                      {r}
                    </button>
                  ))}
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Clinical Discontinuation Reason & Audit Note *</label>
                <textarea
                  rows={3}
                  required
                  placeholder="Specify clinical justification for stopping this medication (e.g., Dosing error, Course complete, Adverse reaction)..."
                  value={discontinueReason}
                  onChange={(e) => setDiscontinueReason(e.target.value)}
                />
              </div>
            </div>

            <div className="modal-footer">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setOrderToDiscontinue(null)}
                disabled={isDiscontinuing}
              >
                Cancel
              </button>
              <button
                type="button"
                id="confirm-discontinue-btn"
                className="btn btn-danger"
                disabled={!discontinueReason.trim() || isDiscontinuing}
                onClick={() => handleDiscontinueOrder(orderToDiscontinue.id, discontinueReason)}
              >
                {isDiscontinuing ? 'Discontinuing Order...' : 'Confirm Discontinue Order'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
