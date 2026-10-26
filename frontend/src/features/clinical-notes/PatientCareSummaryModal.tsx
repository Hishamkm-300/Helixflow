import React from 'react';
import {
  Printer,
  X,
  CheckCircle2,
  AlertTriangle,
  HeartPulse,
  Pill,
  Home,
  Calendar,
  FileCheck2,
  ShieldCheck,
  Stethoscope,
  Sparkles,
} from 'lucide-react';
import { PatientCareSummaryDto } from '../../types';
import './PatientCareSummaryModal.css';

interface PatientCareSummaryModalProps {
  isOpen: boolean;
  onClose: () => void;
  summary: PatientCareSummaryDto | null;
  isLoading?: boolean;
}

export const PatientCareSummaryModal: React.FC<PatientCareSummaryModalProps> = ({
  isOpen,
  onClose,
  summary,
  isLoading = false,
}) => {
  if (!isOpen) return null;

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="patient-summary-modal-backdrop">
      <div className="patient-summary-modal-container max-w-4xl w-full">
        {/* Modal Action Bar (Hidden during printing) */}
        <div className="patient-summary-modal-topbar no-print">
          <div className="topbar-title-group">
            <span className="badge-ai-chip">
              <Sparkles size={14} />
              Output 2: AI Patient Translation
            </span>
            <h2>Verified Patient Care Summary</h2>
          </div>
          <div className="topbar-actions">
            <button
              type="button"
              className="btn btn-primary btn-print"
              onClick={handlePrint}
              disabled={isLoading || !summary}
              title="Print high-contrast patient discharge document or export to PDF"
            >
              <Printer size={16} />
              <span>🖨️ Print for Patient</span>
            </button>
            <button
              type="button"
              className="btn-modal-close"
              onClick={onClose}
              title="Close"
              aria-label="Close"
            >
              <X size={20} />
            </button>
          </div>
        </div>

        {/* Modal Scrollable Body */}
        <div className="patient-summary-modal-scroll">
          {isLoading ? (
            <div className="summary-loading-state">
              <Sparkles size={36} className="animate-spin text-ai" />
              <h3>Translating Clinical Assessment & Plan...</h3>
              <p>
                Grok AI is synthesizing physician findings into plain-language 6th-grade reading level patient care instructions, extracting active medications, and formatting home care directions.
              </p>
            </div>
          ) : !summary ? (
            <div className="summary-loading-state">
              <AlertTriangle size={36} className="text-warning" />
              <h3>No Patient Care Summary Available</h3>
              <p>Please sign and commit the clinical note in the Doctor Workspace to automatically generate the verified patient summary.</p>
            </div>
          ) : (
            /* Printable Clean White High-Contrast Sheet */
            <div
              className="patient-care-sheet bg-white text-slate-900 rounded-xl p-8 shadow-2xl overflow-y-auto max-h-[80vh]"
              id="printable-patient-summary"
            >
              {/* Document Header */}
              <div className="sheet-header">
                <div className="sheet-brand">
                  <div className="hospital-cross-icon text-emerald-600">
                    <Stethoscope size={28} />
                  </div>
                  <div>
                    <h1 className="hospital-title text-slate-900 font-bold">HelixFlow Health System</h1>
                    <div className="hospital-dept text-slate-600 font-medium">Department of Emergency & Acute Medicine</div>
                  </div>
                </div>
                <div className="sheet-doc-badge">
                  <div className="doc-type-label">VERIFIED PATIENT CARE SUMMARY</div>
                  <div className="doc-type-sub text-slate-600">Patient-Friendly After-Care & Discharge Plan</div>
                </div>
              </div>

              {/* Patient & Encounter Metadata Grid */}
              <div className="patient-meta-card">
                <div className="meta-col">
                  <span className="meta-label">Patient Name:</span>
                  <strong className="meta-val text-slate-900 font-bold">{summary.patientName || 'Patient'}</strong>
                </div>
                <div className="meta-col">
                  <span className="meta-label">MRN:</span>
                  <span className="meta-val mono text-slate-800">{summary.mrn || 'MRN-SYN'}</span>
                </div>
                <div className="meta-col">
                  <span className="meta-label">Encounter #:</span>
                  <span className="meta-val mono text-slate-800">{summary.visitNumber || 'ENC-ACTIVE'}</span>
                </div>
                <div className="meta-col">
                  <span className="meta-label">Date of Service:</span>
                  <span className="meta-val text-slate-800">{summary.visitDate || new Date().toLocaleDateString()}</span>
                </div>
                <div className="meta-col">
                  <span className="meta-label">Attending Physician:</span>
                  <strong className="meta-val text-slate-900 font-bold">{summary.doctorName || 'Dr. Robert Smith, MD'}</strong>
                </div>
              </div>

              {/* Section 1: Understanding Your Diagnosis */}
              <div className="sheet-section">
                <div className="section-heading text-slate-900 font-bold text-base">
                  <HeartPulse size={18} className="heading-icon text-blue shrink-0" />
                  <h3 className="text-slate-900 font-bold text-base">Understanding Your Diagnosis</h3>
                </div>
                <div className="diagnosis-box">
                  <p className="diagnosis-text">{summary.diagnosis}</p>
                </div>
              </div>

              {/* Section 2: Your Prescriptions & How to Take Them */}
              <div className="sheet-section">
                <div className="section-heading text-slate-900 font-bold text-base">
                  <Pill size={18} className="heading-icon text-indigo shrink-0" />
                  <h3 className="text-slate-900 font-bold text-base">Your Prescriptions & How to Take Them</h3>
                </div>
                {summary.medications && summary.medications.length > 0 ? (
                  <div className="medications-table-wrapper">
                    <table className="medications-table">
                      <thead>
                        <tr>
                          <th style={{ width: '28%' }}>Medication & Dose</th>
                          <th style={{ width: '22%' }}>How Often to Take</th>
                          <th style={{ width: '50%' }}>Directions & Purpose</th>
                        </tr>
                      </thead>
                      <tbody>
                        {summary.medications.map((m, idx) => (
                          <tr key={idx}>
                            <td>
                              <strong className="text-slate-900 font-bold">{m.medicationName}</strong>
                              <div className="med-dose-tag">{m.dose}</div>
                            </td>
                            <td>
                              <span className="med-freq-tag">{m.frequency}</span>
                            </td>
                            <td className="med-instructions-cell text-slate-700 font-medium text-sm leading-relaxed">{m.instructions}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="empty-sub-box text-slate-700 font-medium text-sm leading-relaxed">
                    No new prescription changes made today. Continue your pre-admission medications as routinely scheduled.
                  </div>
                )}
              </div>

              {/* Section 3: Home Care & Red-Flag Warnings */}
              <div className="sheet-section red-flag-section">
                <div className="section-heading red-heading text-slate-900 font-bold text-base">
                  <Home size={18} className="heading-icon text-emerald-600 shrink-0" />
                  <h3 className="text-slate-900 font-bold text-base">Home Care & Red-Flag Warnings</h3>
                </div>
                <div className="checklist-container">
                  <div className="sub-heading-care text-slate-900 font-bold text-base">
                    HOME RECOVERY &amp; THINGS TO AVOID:
                  </div>
                  {summary.homeCare && summary.homeCare.length > 0 ? (
                    summary.homeCare.map((item, idx) => (
                      <div key={idx} className="checklist-item text-slate-700 font-medium text-sm leading-relaxed">
                        <CheckCircle2 size={16} className="check-icon text-emerald-600 shrink-0" />
                        <span className="checklist-text text-slate-700 font-medium text-sm leading-relaxed">{item}</span>
                      </div>
                    ))
                  ) : (
                    <p className="text-slate-700 font-medium text-sm leading-relaxed">
                      Rest comfortably, maintain hydration, and avoid strenuous physical exertion for the next 48 hours.
                    </p>
                  )}
                </div>

                <div className="red-flag-box" style={{ marginTop: '1.15rem' }}>
                  <div className="red-flag-intro">
                    <AlertTriangle size={18} className="heading-icon text-red shrink-0" style={{ verticalAlign: 'middle', marginRight: '0.35rem' }} />
                    <strong>SEEK IMMEDIATE EMERGENCY CARE OR CALL 911</strong> if you experience any of the following warning signs:
                  </div>
                  <ul className="red-flag-list">
                    {summary.redFlags && summary.redFlags.length > 0 ? (
                      summary.redFlags.map((flag, idx) => (
                        <li key={idx} className="red-flag-item text-red-900 font-medium text-sm leading-relaxed">
                          {flag}
                        </li>
                      ))
                    ) : (
                      <>
                        <li className="red-flag-item text-red-900 font-medium text-sm leading-relaxed">Sudden or worsening shortness of breath or blue lips/fingers.</li>
                        <li className="red-flag-item text-red-900 font-medium text-sm leading-relaxed">Recurrent crushing chest pain, tightness, or pressure.</li>
                        <li className="red-flag-item text-red-900 font-medium text-sm leading-relaxed">Fainting, severe dizziness, confusion, or speech difficulty.</li>
                        <li className="red-flag-item text-red-900 font-medium text-sm leading-relaxed">High fever greater than 101.5°F (38.6°C) not relieved by medication.</li>
                      </>
                    )}
                  </ul>
                </div>
              </div>

              {/* Section 4: Follow-Up Instructions */}
              <div className="sheet-section">
                <div className="section-heading text-slate-900 font-bold text-base">
                  <Calendar size={18} className="heading-icon text-purple shrink-0" />
                  <h3 className="text-slate-900 font-bold text-base">FOLLOW-UP INSTRUCTIONS</h3>
                </div>
                <div className="follow-up-box bg-purple-50 border border-purple-200 text-purple-900 rounded-lg p-4">
                  <p className="follow-up-text text-slate-800 font-medium text-sm leading-relaxed">{summary.followUp}</p>
                </div>
              </div>

              {/* Attending Doctor Verification Badge */}
              <div className="signature-footer-box">
                <div className="sig-wrapper-col">
                  <div className="sig-heading-label text-slate-900 font-bold text-base">Attending Doctor Verification Badge</div>
                  <div className="sig-badge">
                    <div className="sig-icon-wrap text-emerald-600">
                      <ShieldCheck size={26} />
                    </div>
                    <div className="sig-info">
                      <div className="sig-primary text-slate-900 font-bold">
                        {summary.doctorSignature || `Verified by ${summary.doctorName || 'Dr. Robert Smith, MD'}`}
                      </div>
                      <div className="sig-sub text-slate-600 text-xs">
                        Signed by {summary.doctorName || 'Dr. Robert Smith, MD'} &bull; Verified: {summary.signedAt ? new Date(summary.signedAt).toLocaleString() : new Date().toLocaleString()} &bull; HelixFlow EHR Permanent Record
                      </div>
                    </div>
                  </div>
                </div>
                <div className="sig-seal">
                  <FileCheck2 size={16} />
                  <span>LEGAL EMR CERTIFIED</span>
                </div>
              </div>

              {/* Document Disclaimer */}
              <div className="sheet-doc-footer">
                <p className="text-slate-500 text-xs">
                  This Patient Care Summary was translated using assistive clinical AI from physician-verified medical documentation. It does not replace direct instructions given by your doctor during your visit. Keep this document for your personal health records.
                </p>
              </div>
            </div>
          )}
        </div>

        {/* Modal Bottom Footer (Hidden during printing) */}
        <div className="patient-summary-modal-footer no-print">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Close
          </button>
          <button
            type="button"
            className="btn btn-primary btn-print"
            onClick={handlePrint}
            disabled={isLoading || !summary}
          >
            <Printer size={16} />
            <span>🖨️ Print for Patient</span>
          </button>
        </div>
      </div>
    </div>
  );
};
