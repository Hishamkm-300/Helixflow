import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginScreen } from './components/auth/LoginScreen';
import { MainLayout, NavTab } from './components/layout/MainLayout';
import { DashboardScreen } from './components/screens/DashboardScreen';
import { PatientTriageScreen } from './components/screens/PatientTriageScreen';
import { BedManagementScreen } from './components/screens/BedManagementScreen';
import { OtStaffSchedulingScreen } from './components/screens/OtStaffSchedulingScreen';
import { PharmacyMedicationScreen } from './components/screens/PharmacyMedicationScreen';
import { ClinicalSoapAssistantScreen } from './components/screens/ClinicalSoapAssistantScreen';
import { OutbreakSimulationScreen } from './components/screens/OutbreakSimulationScreen';
import { Activity } from 'lucide-react';

const AppContent: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuth();
  const [activeTab, setActiveTab] = useState<NavTab>('dashboard');

  if (isLoading) {
    return (
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--bg-app)',
        color: 'var(--text-secondary)',
        gap: '1rem',
      }}>
        <Activity size={36} color="var(--color-primary)" className="animate-spin" />
        <p style={{ fontFamily: 'var(--font-display)', fontWeight: 600 }}>Initializing HelixFlow Portal...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <LoginScreen />;
  }

  return (
    <MainLayout activeTab={activeTab} onTabChange={setActiveTab}>
      {activeTab === 'dashboard' && <DashboardScreen />}
      {activeTab === 'triage' && <PatientTriageScreen />}
      {activeTab === 'beds' && <BedManagementScreen />}
      {activeTab === 'ot' && <OtStaffSchedulingScreen />}
      {activeTab === 'pharmacy' && <PharmacyMedicationScreen />}
      {activeTab === 'clinical' && <ClinicalSoapAssistantScreen />}
      {activeTab === 'outbreak' && <OutbreakSimulationScreen />}
    </MainLayout>
  );
};

export function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}

export default App;
