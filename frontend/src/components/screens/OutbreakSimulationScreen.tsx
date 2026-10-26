import React, { useState, useEffect } from 'react';
import {
  Radio,
  Sliders,
  AlertTriangle,
  ShieldCheck,
  TrendingUp,
  Activity,
  Play,
  CheckCircle,
  RefreshCw,
  Clock,
  Layers,
  Sparkles,
} from 'lucide-react';
import { analyticsApi } from '../../services/analyticsApi';
import {
  ClusterScanResultDto,
  OutbreakAlertDto,
  WhatIfSimulationRequest,
  WhatIfSimulationResult,
} from '../../types';
import './OutbreakSimulationScreen.css';

export const OutbreakSimulationScreen: React.FC = () => {
  // Sentinel State
  const [alerts, setAlerts] = useState<OutbreakAlertDto[]>([]);
  const [scanResult, setScanResult] = useState<ClusterScanResultDto | null>(null);
  const [scanning, setScanning] = useState(false);

  // What-If Simulation State
  const [simRequest, setSimRequest] = useState<WhatIfSimulationRequest>({
    simulationHours: 24,
    deltaArrivalRatePercent: 30,
    deltaStaffingPercent: -10,
    closedBedsCount: 2,
  });
  const [simResult, setSimResult] = useState<WhatIfSimulationResult | null>(null);
  const [simulating, setSimulating] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);

  const loadSentinelData = async () => {
    try {
      const activeAlerts = await analyticsApi.getActiveAlerts();
      setAlerts(activeAlerts);
    } catch (err) {
      console.error('Failed to load sentinel alerts', err);
    }
  };

  const runInitialSim = async () => {
    setSimulating(true);
    try {
      const res = await analyticsApi.runWhatIfSimulation(simRequest);
      setSimResult(res);
    } catch (err) {
      console.error('Initial simulation run failed', err);
    } finally {
      setSimulating(false);
    }
  };

  useEffect(() => {
    loadSentinelData();
    runInitialSim();
  }, []);

  const handleTriggerScan = async () => {
    setScanning(true);
    setFeedback(null);
    try {
      const result = await analyticsApi.runSurveillanceScan();
      setScanResult(result);
      setAlerts(result.generatedAlerts);
      setFeedback(`Syndromic scan completed: ${result.scanSummary}`);
    } catch (err: any) {
      setFeedback(`Scan error: ${err.message || 'Failed to complete syndromic scan'}`);
    } finally {
      setScanning(false);
    }
  };

  const handleAcknowledgeAlert = async (alertId: number) => {
    try {
      await analyticsApi.acknowledgeAlert(alertId);
      setAlerts((prev) => prev.filter((a) => a.id !== alertId));
      setFeedback(`Alert #${alertId} acknowledged.`);
    } catch (err: any) {
      setFeedback(`Failed to acknowledge alert: ${err.message}`);
    }
  };

  const handleRunSimulation = async (e: React.FormEvent) => {
    e.preventDefault();
    setSimulating(true);
    setFeedback(null);
    try {
      const result = await analyticsApi.runWhatIfSimulation(simRequest);
      setSimResult(result);
      setFeedback(`Simulation completed for ${simRequest.simulationHours}-hour window.`);
    } catch (err: any) {
      setFeedback(`Simulation error: ${err.message || 'Simulation execution failed'}`);
    } finally {
      setSimulating(false);
    }
  };

  const getAlertBadgeClass = (level: string) => {
    switch (level) {
      case 'OUTBREAK_TRIGGER': return 'badge-occupied';
      case 'WARNING': return 'badge-esi-2';
      case 'WATCH': return 'badge-cleaning';
      default: return 'badge-available';
    }
  };

  const getStressBadgeClass = (stress?: string) => {
    switch (stress) {
      case 'COLLAPSED': return 'badge-occupied';
      case 'SATURATED': return 'badge-esi-2';
      case 'STRAINED': return 'badge-cleaning';
      default: return 'badge-available';
    }
  };

  return (
    <div className="outbreak-screen animate-fade-in">
      <div className="screen-header">
        <div>
          <h1>Syndromic Sentinel & Capacity Simulation</h1>
          <p className="screen-subtitle">
            Statistical Z-score outbreak detection over triage complaints & circadian Monte Carlo what-if capacity projections
          </p>
        </div>
      </div>

      {feedback && (
        <div className="screen-alert alert-success">
          <CheckCircle size={18} />
          <span>{feedback}</span>
        </div>
      )}

      {/* Top Section: Outbreak Sentinel Surveillance */}
      <div className="card">
        <div className="card-header">
          <div className="card-title">
            <Radio size={20} className="pulse-icon text-cyan" />
            <span>Syndromic Outbreak Early Warning Sentinel</span>
          </div>
          <button
            className="btn btn-primary btn-sm"
            onClick={handleTriggerScan}
            disabled={scanning}
          >
            <RefreshCw size={14} className={scanning ? 'animate-spin' : ''} />
            <span>{scanning ? 'Scanning 24h Encounters...' : 'Trigger Syndromic Scan'}</span>
          </button>
        </div>

        {scanResult && (
          <div className="syndrome-distribution-grid">
            {Object.entries(scanResult.syndromeDistribution).map(([syndrome, count]) => (
              <div key={syndrome} className="syndrome-badge-box">
                <span className="syn-label">{syndrome.replace('_', ' ')}</span>
                <span className="syn-count">{count} cases (24h)</span>
              </div>
            ))}
          </div>
        )}

        <div className="alerts-container">
          {alerts.length > 0 ? (
            alerts.map((al) => (
              <div key={al.id} className="sentinel-alert-card">
                <div className="alert-card-left">
                  <div className="alert-level-badge">
                    <span className={`badge ${getAlertBadgeClass(al.alertLevel)}`}>
                      {al.alertLevel}
                    </span>
                    <span className="zscore-tag">Z-Score: +{al.zScore}σ</span>
                  </div>
                  <div className="alert-title">
                    Syndrome Cluster: <strong>{al.syndromeCluster.replace('_', ' ')}</strong>
                  </div>
                  <div className="alert-meta">
                    Detected <strong>{al.detectedCount}</strong> presentations (historical baseline: {al.baselineThreshold}/day).
                    Sample Visits: <span className="mono">{al.sampleVisitIds}</span>
                  </div>
                </div>

                <div className="alert-card-right">
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => handleAcknowledgeAlert(al.id)}
                  >
                    Acknowledge Alert
                  </button>
                </div>
              </div>
            ))
          ) : (
            <div className="no-alerts-box">
              <ShieldCheck size={28} className="text-available" />
              <span>No statistical syndromic outbreak spikes detected above baseline threshold.</span>
            </div>
          )}
        </div>
      </div>

      {/* Bottom Section: What-If Capacity Simulator */}
      <div className="card" style={{ marginTop: '1.5rem' }}>
        <div className="card-header">
          <div className="card-title">
            <Sliders size={20} />
            <span>Circadian Discrete-Time What-If Capacity Simulator</span>
          </div>
          <span className="badge badge-ai">Monte Carlo Engine</span>
        </div>

        <form onSubmit={handleRunSimulation} className="simulation-control-panel">
          <div className="slider-control-grid">
            <div className="slider-box">
              <div className="slider-header">
                <label>Emergency Patient Arrival Surge</label>
                <span className="mono text-cyan">
                  {simRequest.deltaArrivalRatePercent >= 0 ? `+${simRequest.deltaArrivalRatePercent}%` : `${simRequest.deltaArrivalRatePercent}%`}
                </span>
              </div>
              <input
                type="range"
                min="-50"
                max="100"
                step="5"
                value={simRequest.deltaArrivalRatePercent}
                onChange={(e) => setSimRequest({ ...simRequest, deltaArrivalRatePercent: parseInt(e.target.value) })}
              />
              <div className="slider-limits">
                <span>-50% (Drop)</span>
                <span>Baseline (0%)</span>
                <span>+100% (Mass Casualty)</span>
              </div>
            </div>

            <div className="slider-box">
              <div className="slider-header">
                <label>Clinical Staffing Capacity</label>
                <span className="mono text-cyan">
                  {simRequest.deltaStaffingPercent >= 0 ? `+${simRequest.deltaStaffingPercent}%` : `${simRequest.deltaStaffingPercent}%`}
                </span>
              </div>
              <input
                type="range"
                min="-50"
                max="50"
                step="5"
                value={simRequest.deltaStaffingPercent}
                onChange={(e) => setSimRequest({ ...simRequest, deltaStaffingPercent: parseInt(e.target.value) })}
              />
              <div className="slider-limits">
                <span>-50% (Severe Shortage)</span>
                <span>Normal (0%)</span>
                <span>+50% (On-Call Surge)</span>
              </div>
            </div>

            <div className="slider-box">
              <div className="slider-header">
                <label>Beds Offline / Maintenance Closure</label>
                <span className="mono text-cyan">{simRequest.closedBedsCount} beds</span>
              </div>
              <input
                type="range"
                min="0"
                max="10"
                step="1"
                value={simRequest.closedBedsCount}
                onChange={(e) => setSimRequest({ ...simRequest, closedBedsCount: parseInt(e.target.value) })}
              />
              <div className="slider-limits">
                <span>0 Closed</span>
                <span>5 Beds</span>
                <span>10 Beds Offline</span>
              </div>
            </div>

            <div className="slider-box">
              <div className="slider-header">
                <label>Simulation Forecast Horizon</label>
                <span className="mono text-cyan">{simRequest.simulationHours} Hours</span>
              </div>
              <input
                type="range"
                min="6"
                max="48"
                step="6"
                value={simRequest.simulationHours}
                onChange={(e) => setSimRequest({ ...simRequest, simulationHours: parseInt(e.target.value) })}
              />
              <div className="slider-limits">
                <span>6 Hours</span>
                <span>24 Hours</span>
                <span>48 Hours</span>
              </div>
            </div>
          </div>

          <button type="submit" className="btn btn-primary sim-submit-btn" disabled={simulating}>
            <Play size={16} />
            <span>{simulating ? 'Computing Trajectory...' : 'Run Discrete Simulation'}</span>
          </button>
        </form>

        {/* Simulation Output Dashboard */}
        {simResult && (
          <div className="simulation-results-container">
            <div className="sim-kpi-row">
              <div className="sim-kpi-card">
                <span className="kpi-label">Projected System Stress</span>
                <span className={`badge ${getStressBadgeClass(simResult.systemStressLevel)}`} style={{ fontSize: '0.9rem', marginTop: '0.4rem' }}>
                  {simResult.systemStressLevel}
                </span>
              </div>
              <div className="sim-kpi-card">
                <span className="kpi-label">Bed Deficit Probability</span>
                <span className="sim-kpi-val text-danger">{simResult.overallBedDeficitProbability}%</span>
              </div>
              <div className="sim-kpi-card">
                <span className="kpi-label">Peak Queue Backlog</span>
                <span className="sim-kpi-val">{simResult.peakQueueDepth} patients</span>
              </div>
              <div className="sim-kpi-card">
                <span className="kpi-label">Projected Avg Wait</span>
                <span className="sim-kpi-val">{Math.round(simResult.projectedAverageWaitMinutes)} mins</span>
              </div>
            </div>

            {/* Recommendations */}
            <div className="recommendations-box">
              <div className="rec-title">
                <Sparkles size={16} className="text-ai" />
                <span>Automated Flow Mitigations & Resource Recommendations</span>
              </div>
              <ul className="rec-list">
                {simResult.mitigationRecommendations.map((rec, idx) => (
                  <li key={idx}>{rec}</li>
                ))}
              </ul>
            </div>

            {/* Hourly Trajectory Chart / Bars */}
            <div className="hourly-trajectory-container">
              <h4 style={{ marginBottom: '0.75rem', fontSize: '0.95rem' }}>
                Hourly Queue & Bed Deficit Risk Progression (Hours 1 - {simResult.simulationHours})
              </h4>
              <div className="trajectory-bars">
                {simResult.hourlyProjections.map((p) => {
                  const barHeight = Math.min(100, Math.max(12, p.queueBacklog * 4));
                  return (
                    <div key={p.hour} className="trajectory-col">
                      <div className="col-val mono">{p.queueBacklog}</div>
                      <div className="col-bar-wrapper">
                        <div
                          className={`col-bar ${p.deficitRisk ? 'deficit' : ''}`}
                          style={{ height: `${barHeight}%` }}
                          title={`Hour ${p.hour}: Queue ${p.queueBacklog}, Available Beds: ${p.bedsAvailable}, Wait: ${p.estimatedWaitTimeMinutes}m`}
                        ></div>
                      </div>
                      <div className="col-hour mono">+{p.hour}h</div>
                    </div>
                  );
                })}
              </div>
              <div className="trajectory-legend">
                <span className="legend-item"><span className="legend-box normal"></span> Queue Backlog</span>
                <span className="legend-item"><span className="legend-box deficit"></span> Bed Shortage Deficit Hour</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
