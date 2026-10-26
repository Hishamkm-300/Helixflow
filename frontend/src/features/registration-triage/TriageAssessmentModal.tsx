import React, { useState, useEffect } from 'react';
import {
  Activity,
  Heart,
  Thermometer,
  Wind,
  Gauge,
  Loader2,
  CheckCircle2,
  X,
  AlertCircle,
  FileText,
} from 'lucide-react';
import { patientApi } from '../../services/patientApi';
import { queryClient } from '../../services/queryCache';
import { TriageAssessmentDto, TriageAssessmentResponseDto } from '../../types';
import './TriageAssessmentModal.css';

export interface TriageAssessmentModalProps {
  isOpen: boolean;
  visitId: number | null;
  visitTitle: string;
  initialAcuity?: number;
  initialNotes?: string;
  onClose: () => void;
  onSuccess: (savedTriage: TriageAssessmentResponseDto, message: string) => void;
}

export const TriageAssessmentModal: React.FC<TriageAssessmentModalProps> = ({
  isOpen,
  visitId,
  visitTitle,
  initialAcuity = 3,
  initialNotes = '',
  onClose,
  onSuccess,
}) => {
  const [triageForm, setTriageForm] = useState<TriageAssessmentDto>(() => ({
    visitId: visitId || 0,
    esiLevel: initialAcuity,
    heartRate: 80,
    bloodPressureSys: 120,
    bloodPressureDia: 80,
    respiratoryRate: 16,
    temperatureCelsius: 37.0,
    o2Saturation: 98,
    consciousnessLevel: 'ALERT',
    triageNotes: initialNotes,
  }));

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (!isOpen || !visitId) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!visitId) return;

    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      const response = await patientApi.submitTriage({
        ...triageForm,
        visitId,
      });

      const successText = `Triage assessment successfully recorded for ${visitTitle} (ESI Level ${triageForm.esiLevel}).`;

      // Invalidate triage queue and Command Center anomalies immediately
      queryClient.invalidateQueries({ queryKey: ['triage-queue'] });
      queryClient.invalidateQueries({ queryKey: ['flow-anomalies'] });
      queryClient.invalidateQueries({ queryKey: ['command-center-metrics'] });

      onSuccess(response, successText);
      onClose();
    } catch (err: any) {
      console.error('Failed to submit clinical triage assessment', err);
      setErrorMessage(err.message || 'Failed to submit triage assessment. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={(e) => { if (e.target === e.currentTarget && !isSubmitting) onClose(); }}>
      <div className="modal-dialog triage-modal-dialog" role="dialog" aria-modal="true" aria-labelledby="triage-modal-title">
        {/* Modal Header */}
        <div className="modal-header">
          <h3 id="triage-modal-title">
            <Activity size={20} className="text-cyan" />
            <span>Clinical Triage Assessment</span>
          </h3>
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={onClose}
            disabled={isSubmitting}
            aria-label="Close modal"
          >
            <X size={16} />
          </button>
        </div>

        {/* Modal Form Wrapping Body and Sticky Footer */}
        <form onSubmit={handleSubmit} className="triage-modal-form">
          {/* Scrollable Body Container */}
          <div className="modal-body triage-modal-body">
            {/* Encounter Information Banner */}
            <div className="triage-patient-banner">
              <Activity size={18} />
              <span>
                Active Encounter: <strong>{visitTitle}</strong>
              </span>
            </div>

            {errorMessage && (
              <div className="triage-error-banner">
                <AlertCircle size={18} />
                <span>{errorMessage}</span>
              </div>
            )}

            {/* Emergency Severity Index (ESI) Selector */}
            <div className="form-group">
              <label className="form-label">
                Emergency Severity Index (ESI Acuity Tier) *
              </label>
              <div className="esi-selector-grid">
                {[1, 2, 3, 4, 5].map((level) => (
                  <button
                    type="button"
                    key={level}
                    className={`esi-option-btn badge-esi-${level} ${triageForm.esiLevel === level ? 'selected' : ''}`}
                    onClick={() => setTriageForm({ ...triageForm, esiLevel: level })}
                  >
                    <div className="esi-opt-num">Level {level}</div>
                    <div className="esi-opt-name">
                      {level === 1 && 'Resuscitation'}
                      {level === 2 && 'Emergent'}
                      {level === 3 && 'Urgent'}
                      {level === 4 && 'Less Urgent'}
                      {level === 5 && 'Non-Urgent'}
                    </div>
                  </button>
                ))}
              </div>
            </div>

            {/* Vital Signs Row 1: Heart Rate & Blood Pressure */}
            <div className="form-row">
              <div className="form-group">
                <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                  <Heart size={14} className="text-danger" />
                  <span>Heart Rate (bpm) *</span>
                </label>
                <input
                  type="number"
                  required
                  min={30}
                  max={250}
                  value={triageForm.heartRate}
                  onChange={(e) => setTriageForm({ ...triageForm, heartRate: parseInt(e.target.value) || 0 })}
                />
              </div>

              <div className="form-group">
                <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                  <Gauge size={14} className="text-cyan" />
                  <span>Systolic BP (mmHg) *</span>
                </label>
                <input
                  type="number"
                  required
                  min={50}
                  max={260}
                  value={triageForm.bloodPressureSys}
                  onChange={(e) => setTriageForm({ ...triageForm, bloodPressureSys: parseInt(e.target.value) || 0 })}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Diastolic BP (mmHg) *</label>
                <input
                  type="number"
                  required
                  min={30}
                  max={160}
                  value={triageForm.bloodPressureDia}
                  onChange={(e) => setTriageForm({ ...triageForm, bloodPressureDia: parseInt(e.target.value) || 0 })}
                />
              </div>
            </div>

            {/* Vital Signs Row 2: Respiratory, Temp, SpO2 */}
            <div className="form-row">
              <div className="form-group">
                <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                  <Wind size={14} className="text-secondary" />
                  <span>Respiratory Rate (bpm) *</span>
                </label>
                <input
                  type="number"
                  required
                  min={6}
                  max={60}
                  value={triageForm.respiratoryRate}
                  onChange={(e) => setTriageForm({ ...triageForm, respiratoryRate: parseInt(e.target.value) || 0 })}
                />
              </div>

              <div className="form-group">
                <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                  <Thermometer size={14} className="text-warning" />
                  <span>Temperature (&deg;C) *</span>
                </label>
                <input
                  type="number"
                  step="0.1"
                  required
                  min={32.0}
                  max={43.0}
                  value={triageForm.temperatureCelsius}
                  onChange={(e) => setTriageForm({ ...triageForm, temperatureCelsius: parseFloat(e.target.value) || 37.0 })}
                />
              </div>

              <div className="form-group">
                <label className="form-label">SpO2 Saturation (%) *</label>
                <input
                  type="number"
                  required
                  min={60}
                  max={100}
                  value={triageForm.o2Saturation}
                  onChange={(e) => setTriageForm({ ...triageForm, o2Saturation: parseInt(e.target.value) || 0 })}
                />
              </div>
            </div>

            {/* Consciousness Level */}
            <div className="form-group">
              <label className="form-label">Consciousness Level (AVPU Acuity)</label>
              <select
                value={triageForm.consciousnessLevel}
                onChange={(e) => setTriageForm({ ...triageForm, consciousnessLevel: e.target.value })}
              >
                <option value="ALERT">Alert (A) - Fully oriented, spontaneous eye opening</option>
                <option value="VERBAL">Responds to Voice (V) - Drowsy, awakens to verbal stimulus</option>
                <option value="PAIN">Responds to Pain (P) - Localizes or withdraws to noxious stimulus</option>
                <option value="UNRESPONSIVE">Unresponsive (U) - Flaccid, no response to stimuli</option>
              </select>
            </div>

            {/* Clinical Assessment Notes */}
            <div className="form-group">
              <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                <FileText size={14} />
                <span>Clinical Triage Notes & Observations</span>
              </label>
              <textarea
                rows={3}
                placeholder="Document clinical observations, pain scale 1-10, allergies, past medical history, or infection precautions..."
                value={triageForm.triageNotes || ''}
                onChange={(e) => setTriageForm({ ...triageForm, triageNotes: e.target.value })}
              />
            </div>
          </div>

          {/* Sticky/Fixed Modal Footer */}
          <div className="modal-footer triage-modal-footer">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={onClose}
              disabled={isSubmitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={isSubmitting}
            >
              {isSubmitting ? (
                <>
                  <Loader2 size={16} className="animate-spin" />
                  <span>Submitting Triage...</span>
                </>
              ) : (
                <>
                  <CheckCircle2 size={16} />
                  <span>Complete Triage Assessment</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
