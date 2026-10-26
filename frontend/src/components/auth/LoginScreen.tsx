import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { Activity, ShieldCheck, UserCheck, AlertCircle, Sparkles } from 'lucide-react';
import './LoginScreen.css';

interface DemoUser {
  label: string;
  roleTitle: string;
  username: string;
  pass: string;
  badgeClass: string;
}

const DEMO_USERS: DemoUser[] = [
  { label: 'System Admin', roleTitle: 'ADMIN', username: 'admin', pass: 'AdminPass123!', badgeClass: 'badge-ai' },
  { label: 'Dr. Robert Smith', roleTitle: 'DOCTOR (ED Lead)', username: 'dr_smith', pass: 'DoctorPass123!', badgeClass: 'badge-available' },
  { label: 'Dr. Sarah Adams', roleTitle: 'DOCTOR (Surgeon)', username: 'dr_adams', pass: 'SurgeonPass123!', badgeClass: 'badge-available' },
  { label: 'Nurse Joy Patel', roleTitle: 'NURSE (Triage)', username: 'nurse_joy', pass: 'NursePass123!', badgeClass: 'badge-cleaning' },
  { label: 'Dan Chen, PharmD', roleTitle: 'PHARMACIST', username: 'pharmacist_dan', pass: 'PharmPass123!', badgeClass: 'badge-esi-5' },
  { label: 'Anna Kowalski', roleTitle: 'RECEPTIONIST (Intake)', username: 'rec_anna', pass: 'ReceptPass123!', badgeClass: 'badge-maintenance' },
];

export const LoginScreen: React.FC = () => {
  const { login } = useAuth();
  const [username, setUsername] = useState('dr_smith');
  const [password, setPassword] = useState('DoctorPass123!');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username, password);
    } catch (err: any) {
      setError(err.message || 'Login failed. Please verify credentials.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSelectDemoUser = (user: DemoUser) => {
    setUsername(user.username);
    setPassword(user.pass);
    setError(null);
  };

  return (
    <div className="login-container">
      <div className="login-backdrop-glow"></div>
      
      <div className="login-card animate-fade-in">
        <div className="login-brand">
          <div className="login-brand-icon">
            <Activity size={32} />
          </div>
          <div className="login-brand-text">
            <h1>HelixFlow</h1>
            <p className="login-tagline">AI Hospital Flow Intelligence & Early Warning System</p>
          </div>
        </div>

        <div className="login-notice">
          <ShieldCheck size={16} />
          <span>Clinical Decision Support System &bull; Non-Diagnostic Prototype</span>
        </div>

        {error && (
          <div className="login-error-alert">
            <AlertCircle size={18} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="login-form">
          <div className="form-group">
            <label className="form-label">Username</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Enter username"
              required
              autoFocus
            />
          </div>

          <div className="form-group">
            <label className="form-label">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter password"
              required
            />
          </div>

          <button type="submit" className="btn btn-primary login-btn" disabled={submitting}>
            {submitting ? 'Authenticating...' : 'Sign In to Hospital Portal'}
          </button>
        </form>

        <div className="demo-accounts-section">
          <div className="demo-accounts-header">
            <Sparkles size={14} />
            <span>Instant Demo Switcher (Pre-Seeded Roles)</span>
          </div>
          <div className="demo-accounts-grid">
            {DEMO_USERS.map((u) => (
              <button
                key={u.username}
                type="button"
                className={`demo-user-chip ${username === u.username ? 'active' : ''}`}
                onClick={() => handleSelectDemoUser(u)}
              >
                <div className="demo-chip-name">{u.label}</div>
                <div className="demo-chip-role">{u.roleTitle}</div>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
