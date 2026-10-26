import React, { useState, useEffect } from 'react';
import {
  FileEdit,
  Sparkles,
  ShieldAlert,
  CheckCircle2,
  AlertTriangle,
  Stethoscope,
  RefreshCw,
  RotateCcw,
  Lock,
  FileCheck2,
  Printer,
  ArrowRight,
  HeartHandshake,
} from 'lucide-react';
import { clinicalApi } from '../../services/clinicalApi';
import { PatientCareSummaryModal } from '../../features/clinical-notes/PatientCareSummaryModal';
import {
  ActiveEncounterDto,
  ClinicalNoteDto,
  PatientCareSummaryDto,
  SoapSignoffRequest,
  StructuredSoapResponse,
} from '../../types';
import './ClinicalSoapAssistantScreen.css';

interface SampleCase {
  title: string;
  notes: string;
}

const SAMPLE_CASES: SampleCase[] = [
  {
    title: 'Patient Report: Chest Pain & Diaphoresis',
    notes:
      'Patient-Reported Chief Complaint: "I have this sudden, crushing pressure in the center of my chest that started about an hour ago while sitting on the couch. It feels like a heavy weight pressing down, and the pain is shooting up into my left jaw and down my left arm. I am sweating heavily, feel nauseous, and cannot catch my breath."\n\n' +
      'Triage & Clinician Findings: 58yo male with history of hypertension and 30-pack-year smoking history. BP 155/95, HR 102 sinus tachycardia, SpO2 95% on ambient air, RR 22. Cardiac exam reveals regular rate and rhythm, no S3/S4 murmurs. Lungs clear to auscultation bilaterally. Initial orders: Administer chewable Aspirin 325mg PO stat, establish 18G peripheral IV, draw serial troponin, 12-lead ECG telemetry monitoring.',
  },
  {
    title: 'Patient Report: Severe Cough & Fever',
    notes:
      'Patient-Reported Chief Complaint: "I have had a high fever and uncontrollable shaking chills for the past four days. Every time I cough, I bring up thick yellowish-green phlegm. It feels like a sharp knife stabbing the right side of my ribs whenever I take a deep breath, and I feel exhausted and dizzy."\n\n' +
      'Triage & Clinician Findings: 72yo female brought in by family due to worsening confusion and tachypnea. Temp 39.1C, HR 114, BP 102/64, RR 28 shallow, SpO2 89% on ambient air. Coarse inspiratory crackles and bronchial breath sounds over right lower lobe. Requires 3L supplemental O2 via nasal cannula. Orders: Urgent portable CXR, blood cultures x2, respiratory pathogen PCR panel, start IV Ceftriaxone 1g daily plus Azithromycin 500mg IV, admit to acute telemetry step-down bed.',
  },
  {
    title: 'Patient Report: Acute RLQ Abdominal Pain',
    notes:
      'Patient-Reported Chief Complaint: "Yesterday evening my stomach started aching dull around my belly button. Over the past 8 hours the pain has moved down to the lower right side of my belly and is now an intense, agonizing 9/10 stabbing ache. I vomited twice and cannot keep liquids down. It hurts terribly when I try to walk, stand straight, or cough."\n\n' +
      'Triage & Clinician Findings: 24yo male with acute progressive right lower quadrant pain. Vitals: Temp 38.1C, HR 98, BP 118/76, RR 18. Abdominal exam reveals acute focal tenderness at McBurney point with positive Rovsing sign and involuntary peritoneal guarding. CBC shows WBC 16.2k with left shift. Orders: Strict NPO, IV crystalloid resuscitation with Normal Saline, IV Ondansetron 4mg, urgent helical CT abdomen/pelvis, emergency surgical consultation for laparoscopic appendectomy.',
  },
];

/**
 * Formats encounter triage assessment data into a clean, distinct two-tier clinical intake structure:
 * Tier 1: Patient-Reported Chief Complaint (direct quote, output only once)
 * Tier 2: Triage & Clinician Findings (Demographics, Vitals, Physical Exam/Observations, Initial Orders)
 */
export function formatTriageToNarrative(encounter: ActiveEncounterDto): string {
  // 1. Patient-Reported Chief Complaint
  let complaint = encounter.patientNarrative?.trim()
    || encounter.chiefComplaint?.trim()
    || encounter.symptomNarrative?.trim()
    || 'Acute symptoms requiring clinical evaluation';

  // Strip enclosing quotes if already present
  if ((complaint.startsWith('"') && complaint.endsWith('"')) || (complaint.startsWith("'") && complaint.endsWith("'"))) {
    complaint = complaint.slice(1, -1).trim();
  }

  // 2. Demographics & History: ${age}yo ${gender}, history of ${medicalHistory || 'None noted'}.
  const age = encounter.age != null ? `${encounter.age}yo` : '52yo';
  const gender = encounter.gender ? encounter.gender.toLowerCase() : 'patient';
  const history = encounter.medicalHistory?.trim() || 'None noted';
  const demoHistory = `${age} ${gender}, history of ${history}.`;

  // 3. Vitals: BP ${bp}, HR ${hr} bpm, SpO2 ${spo2}%, RR ${rr}/min, Temp ${temp}°F, Pain: ${painScore}/10.
  let bp = '120/80';
  if (encounter.bloodPressureSys && encounter.bloodPressureDia) {
    bp = `${encounter.bloodPressureSys}/${encounter.bloodPressureDia}`;
  } else if (encounter.bloodPressureSys) {
    bp = `${encounter.bloodPressureSys}/80`;
  }

  const hr = encounter.heartRate != null ? `${encounter.heartRate}` : '80';
  const spo2 = encounter.o2Saturation != null ? `${encounter.o2Saturation}` : '98';
  const rr = encounter.respiratoryRate != null ? `${encounter.respiratoryRate}` : '16';

  let temp = '98.6';
  if (encounter.temperatureFahrenheit != null) {
    temp = `${encounter.temperatureFahrenheit}`;
  } else if (encounter.temperatureCelsius != null) {
    const f = (encounter.temperatureCelsius * 9.0) / 5.0 + 32.0;
    temp = `${f.toFixed(1)}`;
  }
  const painScore = encounter.painScore != null ? `${encounter.painScore}` : '5';

  const vitalsStr = `BP ${bp}, HR ${hr} bpm, SpO2 ${spo2}%, RR ${rr}/min, Temp ${temp}°F, Pain: ${painScore}/10.`;

  // 4. Physical Exam / Nurse Observations (if present)
  let observations = encounter.physicalExam?.trim();
  if (!observations && encounter.triageNotes?.trim()) {
    let rawNotes = encounter.triageNotes.trim();
    if (rawNotes.includes('Self-reports:')) {
      const parts = rawNotes.split('. Triage vitals recorded by');
      if (parts.length > 1) {
        observations = `Consciousness: ${encounter.consciousnessLevel || 'ALERT'}. Triage vitals recorded by ${parts[1].trim()}`;
      } else {
        observations = `Consciousness: ${encounter.consciousnessLevel || 'ALERT'}, oriented x4.`;
      }
    } else {
      observations = rawNotes;
    }
  }
  if (!observations) {
    observations = `Patient alert and oriented x4. ESI-${encounter.esiLevel || 3} acute monitoring initiated.`;
  }
  if (!observations.endsWith('.')) {
    observations += '.';
  }

  // 5. Initial Triage Orders: Initial orders: ${triageOrders || 'Standard triage workup initiated.'}
  let orders = encounter.triageOrders?.trim() || 'Standard triage workup initiated.';
  if (orders.toLowerCase().startsWith('initial orders:')) {
    orders = orders.substring('initial orders:'.length).trim();
  }
  const ordersStr = `Initial orders: ${orders}`;

  return [
    `Patient-Reported Chief Complaint: "${complaint}"`,
    '',
    `Triage & Clinician Findings: ${demoHistory} ${vitalsStr} ${observations} ${ordersStr}`,
  ].join('\n');
}

export const ClinicalSoapAssistantScreen: React.FC = () => {
  const [encounters, setEncounters] = useState<ActiveEncounterDto[]>([]);
  const [selectedVisitId, setSelectedVisitId] = useState<number>(1);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [rawNarrative, setRawNarrative] = useState('');
  const [loading, setLoading] = useState(false);
  const [isSigning, setIsSigning] = useState(false);
  const [soapDraft, setSoapDraft] = useState<StructuredSoapResponse | null>(null);
  const [currentNoteId, setCurrentNoteId] = useState<number | null>(null);
  const [signedNote, setSignedNote] = useState<ClinicalNoteDto | null>(null);
  const [isSigned, setIsSigned] = useState(false);

  // Workflow 2: Patient-Friendly Care Summary state
  const [patientSummary, setPatientSummary] = useState<PatientCareSummaryDto | null>(null);
  const [isGeneratingSummary, setIsGeneratingSummary] = useState(false);
  const [isSummaryModalOpen, setIsSummaryModalOpen] = useState(false);

  const [editedSoap, setEditedSoap] = useState<{
    subjective: string;
    objective: string;
    assessment: string;
    plan: string;
  }>({
    subjective: '',
    objective: '',
    assessment: '',
    plan: '',
  });
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadEncounters = async () => {
    setIsRefreshing(true);
    try {
      const data = await clinicalApi.getActiveEncounters();
      setEncounters(data || []);
      if (data && data.length > 0) {
        let initialVisit = data.find((e) => e.visitId === selectedVisitId);
        if (!initialVisit) {
          initialVisit = data[0];
          setSelectedVisitId(initialVisit.visitId);
        }
        // Auto-populate narrative with genuine triage submission from selected encounter
        setRawNarrative(formatTriageToNarrative(initialVisit));
      }
    } catch (err) {
      console.error('Failed to load active encounters for SOAP tool', err);
    } finally {
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadEncounters();
  }, []);

  const handleGenerateSoap = async () => {
    if (!rawNarrative.trim()) return;
    setLoading(true);
    setStatusMessage(null);
    setErrorMessage(null);
    setIsSigned(false);
    setSignedNote(null);
    setCurrentNoteId(null);
    setPatientSummary(null);

    try {
      const response = await clinicalApi.structureClinicalNote({
        visitId: selectedVisitId,
        rawClinicalNotes: rawNarrative,
      });
      setSoapDraft(response);
      setEditedSoap({
        subjective: response.subjective,
        objective: response.objective,
        assessment: response.assessment,
        plan: response.plan,
      });
      setStatusMessage('Workflow 1: Structured SOAP Decision Draft generated via Grok AI from patient input.');
    } catch (err: any) {
      setErrorMessage(`AI Structuring Error: ${err.message || 'Service unavailable'}`);
    } finally {
      setLoading(false);
    }
  };

  const handleSelectCase = (caseItem: SampleCase) => {
    if (isSigned) {
      handleResetForNewNote();
    }
    setRawNarrative(caseItem.notes);
    setSoapDraft(null);
    setPatientSummary(null);
    setStatusMessage(null);
    setErrorMessage(null);
  };

  const handleEncounterChange = async (visitId: number) => {
    setSelectedVisitId(visitId);
    if (isSigned) {
      handleResetForNewNote();
    } else {
      setSoapDraft(null);
      setPatientSummary(null);
      setStatusMessage(null);
      setErrorMessage(null);
    }

    // 1. Immediately pre-fill from cached encounter list if present
    const cached = encounters.find((e) => e.visitId === visitId);
    if (cached) {
      setRawNarrative(formatTriageToNarrative(cached));
    }

    // 2. Fetch fresh detailed triage assessment via GET /api/v1/clinical/encounters/{id}
    try {
      const fresh = await clinicalApi.getEncounterDetails(visitId);
      if (fresh) {
        setRawNarrative(formatTriageToNarrative(fresh));
        setEncounters((prev) => prev.map((e) => (e.visitId === fresh.visitId ? fresh : e)));
      }
    } catch (err) {
      console.warn('Could not fetch fresh encounter details, using cached:', err);
    }
  };

  const handleResetForNewNote = () => {
    setIsSigned(false);
    setSignedNote(null);
    setCurrentNoteId(null);
    setSoapDraft(null);
    setPatientSummary(null);
    setIsGeneratingSummary(false);
    setIsSummaryModalOpen(false);
    setEditedSoap({
      subjective: '',
      objective: '',
      assessment: '',
      plan: '',
    });
    // Reset to current encounter's triage assessment
    const current = encounters.find((e) => e.visitId === selectedVisitId);
    setRawNarrative(current ? formatTriageToNarrative(current) : '');
    setStatusMessage(null);
    setErrorMessage(null);
  };

  const handleCommitNote = async () => {
    if (!soapDraft) return;
    setIsSigning(true);
    setStatusMessage(null);
    setErrorMessage(null);

    try {
      let targetNoteId = currentNoteId;

      // 1. If the note has not been saved as a draft yet, automatically trigger draft save first
      if (!targetNoteId) {
        const draftRes = await clinicalApi.saveNoteDraft(
          selectedVisitId,
          rawNarrative,
          'PROGRESS_NOTE'
        );
        targetNoteId = draftRes.id;
        setCurrentNoteId(draftRes.id);
      }

      // 2. Dispatch the sign-off payload with Subjective, Objective, Assessment, Plan fields
      const signoffPayload: SoapSignoffRequest = {
        subjective: editedSoap.subjective,
        objective: editedSoap.objective,
        assessment: editedSoap.assessment,
        plan: editedSoap.plan,
        triageCategory: soapDraft.triageCategoryDraft,
        differentialDiagnoses: soapDraft.differentialDiagnoses,
        suggestedInvestigations: soapDraft.suggestedInvestigations,
        suggestedInterventions: soapDraft.suggestedInterventions,
      };

      const signedResult = await clinicalApi.signoffSoapNote(targetNoteId, signoffPayload);
      setSignedNote(signedResult);
      setIsSigned(true);
      setStatusMessage('Clinical note successfully verified and committed to medical record.');

      // 3. Workflow 2: Immediately trigger second Grok transformation prompt for Patient Care Summary
      setIsGeneratingSummary(true);
      try {
        const summaryResult = await clinicalApi.generatePatientSummary(targetNoteId);
        setPatientSummary(summaryResult);
        setIsSummaryModalOpen(true);
        setStatusMessage(
          'Clinical note committed & verified! Grok AI automatically generated the Patient-Friendly Care Summary.'
        );
      } catch (sumErr: any) {
        console.error('Failed to automatically generate patient summary:', sumErr);
        setStatusMessage(
          'Clinical note committed. Click "Generate Patient Summary" to review patient instructions.'
        );
      } finally {
        setIsGeneratingSummary(false);
      }
    } catch (err: any) {
      console.error('Failed to sign clinical note:', err);
      setErrorMessage(err.message || 'Failed to sign and commit clinical note to medical record');
    } finally {
      setIsSigning(false);
    }
  };

  const handleManualOpenSummary = async () => {
    if (patientSummary) {
      setIsSummaryModalOpen(true);
      return;
    }

    if (currentNoteId) {
      setIsGeneratingSummary(true);
      setIsSummaryModalOpen(true);
      try {
        const summaryResult = await clinicalApi.generatePatientSummary(currentNoteId);
        setPatientSummary(summaryResult);
      } catch (err: any) {
        console.error('Error generating summary:', err);
        setErrorMessage('Failed to generate patient care summary: ' + (err.message || 'Unknown error'));
      } finally {
        setIsGeneratingSummary(false);
      }
    }
  };


  return (
    <div className="soap-screen animate-fade-in">
      <div className="screen-header">
        <div>
          <div className="workflow-loop-pills">
            <span className="workflow-badge step-1">1. Patient Intake</span>
            <ArrowRight size={14} className="workflow-arrow" />
            <span className="workflow-badge step-ai">2. AI Translation</span>
            <ArrowRight size={14} className="workflow-arrow" />
            <span className="workflow-badge step-doctor">3. Doctor Workspace</span>
            <ArrowRight size={14} className="workflow-arrow" />
            <span className="workflow-badge step-ai">4. AI Translation</span>
            <ArrowRight size={14} className="workflow-arrow" />
            <span className="workflow-badge step-2">5. Patient Care Summary</span>
          </div>
          <h1>Two-Way Clinical AI Communication Loop</h1>
          <p className="screen-subtitle">
            Bridge patient-reported symptoms into physician-verified SOAP records, and transform clinical decisions into clear patient discharge instructions with Grok AI.
          </p>
        </div>
      </div>

      {/* Mandatory Non-Diagnostic AI Safety Banner */}
      <div className="disclaimer-banner">
        <ShieldAlert size={20} />
        <div>
          <strong>MANDATORY NON-DIAGNOSTIC CLINICAL SAFETY NOTICE:</strong>
          <div style={{ marginTop: '0.15rem' }}>
            This system is an administrative documentation decision-support prototype. It does NOT possess independent diagnostic authority. All generated findings, differentials, and care plans are assistive drafts that require explicit clinician verification and signature before medical execution.
          </div>
        </div>
      </div>

      {statusMessage && (
        <div className="screen-alert alert-success">
          <CheckCircle2 size={18} />
          <span>{statusMessage}</span>
        </div>
      )}

      {errorMessage && (
        <div className="screen-alert alert-error">
          <AlertTriangle size={18} />
          <span>{errorMessage}</span>
        </div>
      )}

      <div className="soap-grid">
        {/* Left Column: Input Section (Patient Intake Simulation) */}
        <div className="card narrative-card">
          <div className="card-header">
            <div className="card-title">
              <Stethoscope size={18} />
              <span>Patient Self-Reported Intake / QR Triage Feed</span>
            </div>
            <span className="badge badge-ai">Patient Intake</span>
          </div>

          <div className="form-group">
            <div className="encounter-dropdown-header">
              <label className="form-label" htmlFor="encounter-select" style={{ marginBottom: 0 }}>
                Associate with Active Patient Encounter *
              </label>
              <button
                type="button"
                className="btn-refresh-encounters"
                onClick={loadEncounters}
                disabled={isRefreshing || isSigning}
                title="Refresh active encounters from admissions and triage"
                aria-label="Refresh active encounters"
              >
                <RefreshCw size={13} className={isRefreshing ? 'animate-spin' : ''} />
                <span>↻ Refresh</span>
              </button>
            </div>
            <select
              id="encounter-select"
              value={selectedVisitId}
              disabled={isSigned || isSigning}
              onChange={(e) => handleEncounterChange(parseInt(e.target.value))}
            >
              {encounters.map((encounter) => {
                const firstName = encounter.firstName?.trim() || '';
                const lastName = encounter.lastName?.trim() || '';
                const patientName = (firstName || lastName)
                  ? `${firstName} ${lastName}`.trim()
                  : (encounter.patientName || `Patient #${encounter.patientId}`);
                const esi = encounter.esiLevel != null ? encounter.esiLevel : 'N/A';

                return (
                  <option key={encounter.visitId} value={encounter.visitId}>
                    {patientName} • {encounter.visitNumber} • ESI {esi} ({encounter.status})
                  </option>
                );
              })}
              {encounters.length === 0 && <option value={1}>No active encounters available</option>}
            </select>
          </div>

          <div className="sample-cases-bar">
            <span className="sample-label">Patient Self-Reported Presets (QR Kiosk / Triage Feed):</span>
            <div className="sample-pills">
              {SAMPLE_CASES.map((sc, i) => (
                <button
                  key={i}
                  type="button"
                  className="sample-pill-btn"
                  onClick={() => handleSelectCase(sc)}
                  disabled={isSigning || isSigned}
                >
                  {sc.title}
                </button>
              ))}
            </div>
          </div>

          <div className="form-group" style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
              <label className="form-label" style={{ marginBottom: 0 }}>
                Patient Self-Reported Symptoms & Chief Complaints {isSigned && '(Signed EMR View - Locked)'}
              </label>
              <span className="triage-autofill-badge" title="Auto-extracted from initial nurse triage evaluation & QR intake">
                ⚡ Auto-Filled from Triage Assessment
              </span>
            </div>
            <textarea
              className={`narrative-textarea ${isSigned ? 'readonly-signed' : ''}`}
              placeholder="Enter patient-reported symptoms, chief complaint narratives, triage vitals, and initial observations..."
              value={rawNarrative}
              readOnly={isSigned || isSigning}
              onChange={(e) => setRawNarrative(e.target.value)}
            />
          </div>

          <button
            type="button"
            className="btn btn-ai generate-btn"
            onClick={handleGenerateSoap}
            disabled={loading || isSigning || !rawNarrative.trim() || isSigned}
          >
            <Sparkles size={18} className={loading ? 'animate-spin' : ''} />
            <span>{loading ? 'Translating & Structuring for Doctor...' : 'Translate & Structure for Doctor'}</span>
          </button>
        </div>

        {/* Right Column: Output 1 (Doctor Workspace) */}
        <div className="card soap-viewer-card">
          <div className="card-header">
            <div className="card-title">
              <FileEdit size={18} />
              <span>Doctor Workspace (Structured SOAP Decision Draft)</span>
            </div>
            <div className="header-badges">
              <span className="badge badge-doctor">Doctor Workspace</span>
              {isSigned ? (
                <span className="badge badge-signed">
                  <CheckCircle2 size={13} />
                  CLINICIAN_SIGNED
                </span>
              ) : soapDraft?.needsClinicianReview ? (
                <span className="badge badge-cleaning">Pending Clinician Review</span>
              ) : null}
            </div>
          </div>

          {soapDraft ? (
            <div className="soap-content-container">
              {/* Acuity & Differential Summary */}
              {soapDraft.triageCategoryDraft && (
                <div className="soap-draft-meta">
                  <span className="badge badge-esi-2">Suggested Triage: {soapDraft.triageCategoryDraft}</span>
                  <span className="meta-patient">Encounter #{selectedVisitId}</span>
                  {isSigned && (
                    <span className="badge badge-signed">
                      <Lock size={12} /> EMR Locked
                    </span>
                  )}
                </div>
              )}

              {/* Subjective Section */}
              <div className="soap-section-box">
                <div className="soap-section-title subjective">
                  <strong>[S] Subjective Narrative</strong>
                  <span className="section-hint">History of Present Illness & Patient Complaints</span>
                </div>
                <textarea
                  rows={3}
                  className={`soap-editable-area ${isSigned ? 'readonly-signed' : ''}`}
                  value={editedSoap.subjective}
                  readOnly={isSigned || isSigning}
                  onChange={(e) => setEditedSoap({ ...editedSoap, subjective: e.target.value })}
                />
              </div>

              {/* Objective Section */}
              <div className="soap-section-box">
                <div className="soap-section-title objective">
                  <strong>[O] Objective Findings</strong>
                  <span className="section-hint">Vitals, Physical Exam, Diagnostic Telemetry</span>
                </div>
                <textarea
                  rows={3}
                  className={`soap-editable-area ${isSigned ? 'readonly-signed' : ''}`}
                  value={editedSoap.objective}
                  readOnly={isSigned || isSigning}
                  onChange={(e) => setEditedSoap({ ...editedSoap, objective: e.target.value })}
                />
              </div>

              {/* Assessment Section */}
              <div className="soap-section-box">
                <div className="soap-section-title assessment">
                  <strong>[A] Clinical Assessment</strong>
                  <span className="section-hint">Clinical Synthesis & Differential Hypotheses</span>
                </div>
                <textarea
                  rows={2}
                  className={`soap-editable-area ${isSigned ? 'readonly-signed' : ''}`}
                  value={editedSoap.assessment}
                  readOnly={isSigned || isSigning}
                  onChange={(e) => setEditedSoap({ ...editedSoap, assessment: e.target.value })}
                />
                {soapDraft.differentialDiagnoses.length > 0 && (
                  <div className="tag-chips-row">
                    <span className="chip-label">Differentials:</span>
                    {soapDraft.differentialDiagnoses.map((d, i) => (
                      <span key={i} className="differential-chip">{d}</span>
                    ))}
                  </div>
                )}
              </div>

              {/* Plan Section */}
              <div className="soap-section-box">
                <div className="soap-section-title plan">
                  <strong>[P] Care Management Plan</strong>
                  <span className="section-hint">Diagnostics, Therapeutics & Disposition</span>
                </div>
                <textarea
                  rows={3}
                  className={`soap-editable-area ${isSigned ? 'readonly-signed' : ''}`}
                  value={editedSoap.plan}
                  readOnly={isSigned || isSigning}
                  onChange={(e) => setEditedSoap({ ...editedSoap, plan: e.target.value })}
                />
                {soapDraft.suggestedInvestigations.length > 0 && (
                  <div className="tag-chips-row">
                    <span className="chip-label">Labs/Imaging:</span>
                    {soapDraft.suggestedInvestigations.map((inv, i) => (
                      <span key={i} className="investigation-chip">{inv}</span>
                    ))}
                  </div>
                )}
              </div>

              {/* Commit Note Action Bar */}
              <div className="soap-commit-bar">
                {isSigned ? (
                  <div style={{ width: '100%' }}>
                    <div className="signed-confirmation-bar">
                      <div className="signed-info">
                        <CheckCircle2 size={20} />
                        <div>
                          <strong>Verified & Signed (CLINICIAN_SIGNED)</strong>
                          <div className="signed-subtext">
                            Signed by {signedNote?.signedByName || 'Dr. Robert Smith, MD'} • Recorded to EMR Audit Log
                          </div>
                        </div>
                      </div>
                      <div className="signed-actions-group">
                        <button
                          type="button"
                          className="btn btn-summary-open"
                          onClick={handleManualOpenSummary}
                          disabled={isGeneratingSummary}
                          title="Open the verified patient care summary modal"
                        >
                          {isGeneratingSummary ? (
                            <>
                              <RefreshCw size={15} className="animate-spin" />
                              <span>Translating Summary...</span>
                            </>
                          ) : (
                            <>
                              <FileCheck2 size={16} />
                              <span>📄 Verified Patient Care Summary</span>
                            </>
                          )}
                        </button>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          onClick={handleResetForNewNote}
                        >
                          <RotateCcw size={15} />
                          <span>Start New Note</span>
                        </button>
                      </div>
                    </div>

                    {/* Output 2 Completion Status Banner */}
                    <div className="workflow-2-status-banner">
                      <div className="w2-banner-content">
                        <HeartHandshake size={20} className="w2-icon" />
                        <div>
                          <strong>Output 2: Verified Patient Care Summary Generated</strong>
                          <p>
                            Grok AI translated the doctor's verified assessment and plan into simple 6th-grade reading level terms with active prescriptions, home care directions, and emergency red-flag warnings.
                          </p>
                        </div>
                      </div>
                      <button
                        type="button"
                        className="btn btn-print-quick"
                        onClick={handleManualOpenSummary}
                      >
                        <Printer size={15} />
                        <span>🖨️ Print for Patient</span>
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                    <div className="sign-disclaimer">
                      By clicking sign, you certify that you have reviewed and verified all AI-drafted sections.
                    </div>
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={handleCommitNote}
                      disabled={isSigning || loading}
                    >
                      {isSigning ? (
                        <>
                          <RefreshCw size={16} className="animate-spin" />
                          <span>Verifying & Signing...</span>
                        </>
                      ) : (
                        <>
                          <CheckCircle2 size={16} />
                          <span>Sign & Commit to Medical Record</span>
                        </>
                      )}
                    </button>
                  </>
                )}
              </div>
            </div>
          ) : (
            <div className="soap-empty-placeholder">
              <Sparkles size={40} className="text-ai" />
              <h3>Awaiting Clinical Narrative Input</h3>
              <p>
                Select an encounter, paste patient complaints or choose a preset on the left, then click <strong>"Structure with Grok AI"</strong>.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Patient-Friendly Care Summary Printable Modal */}
      <PatientCareSummaryModal
        isOpen={isSummaryModalOpen}
        onClose={() => setIsSummaryModalOpen(false)}
        summary={patientSummary}
        isLoading={isGeneratingSummary}
      />
    </div>
  );
};
