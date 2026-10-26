import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import {
  LayoutDashboard,
  UserPlus,
  BedDouble,
  CalendarDays,
  Pill,
  FileEdit,
  Radio,
  ChevronLeft,
  ChevronRight,
  LogOut,
  Activity,
  Shield,
  User as UserIcon,
  Bell,
} from 'lucide-react';
import './MainLayout.css';

export type NavTab = 'dashboard' | 'triage' | 'beds' | 'ot' | 'pharmacy' | 'clinical' | 'outbreak';

interface NavItem {
  id: NavTab;
  label: string;
  icon: React.ReactNode;
  allowedRoles: Array<'ADMIN' | 'DOCTOR' | 'NURSE' | 'PHARMACIST' | 'RECEPTIONIST'>;
  badge?: string;
}

const NAV_ITEMS: NavItem[] = [
  {
    id: 'dashboard',
    label: 'Flow Command Center',
    icon: <LayoutDashboard size={20} />,
    allowedRoles: ['ADMIN', 'DOCTOR', 'NURSE', 'PHARMACIST', 'RECEPTIONIST'],
  },
  {
    id: 'triage',
    label: 'Intake & Triage Queue',
    icon: <UserPlus size={20} />,
    allowedRoles: ['ADMIN', 'RECEPTIONIST', 'NURSE', 'DOCTOR'],
  },
  {
    id: 'beds',
    label: 'Ward Bed Grid',
    icon: <BedDouble size={20} />,
    allowedRoles: ['ADMIN', 'NURSE', 'DOCTOR'],
  },
  {
    id: 'ot',
    label: 'OT & Staff Roster',
    icon: <CalendarDays size={20} />,
    allowedRoles: ['ADMIN', 'DOCTOR', 'NURSE'],
  },
  {
    id: 'pharmacy',
    label: 'Pharmacy & Drug Safety',
    icon: <Pill size={20} />,
    allowedRoles: ['ADMIN', 'PHARMACIST', 'DOCTOR'],
  },
  {
    id: 'clinical',
    label: 'SOAP AI Assistant',
    icon: <FileEdit size={20} />,
    allowedRoles: ['ADMIN', 'DOCTOR', 'NURSE'],
    badge: 'AI',
  },
  {
    id: 'outbreak',
    label: 'Sentinel & Simulation',
    icon: <Radio size={20} />,
    allowedRoles: ['ADMIN', 'DOCTOR', 'NURSE'],
  },
];

interface MainLayoutProps {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
  children: React.ReactNode;
}

export const MainLayout: React.FC<MainLayoutProps> = ({ activeTab, onTabChange, children }) => {
  const { user, logout, hasRole } = useAuth();
  const [collapsed, setCollapsed] = useState(false);

  const visibleNav = NAV_ITEMS.filter((item) => hasRole(item.allowedRoles));

  return (
    <div className="layout-root">
      {/* Sidebar */}
      <aside className={`layout-sidebar ${collapsed ? 'collapsed' : ''}`}>
        <div className="sidebar-header">
          <div className="sidebar-logo">
            <Activity className="logo-icon" size={24} />
            {!collapsed && <span className="logo-text">HelixFlow</span>}
          </div>
          <button
            className="collapse-toggle-btn"
            onClick={() => setCollapsed(!collapsed)}
            title={collapsed ? 'Expand Sidebar' : 'Collapse Sidebar'}
          >
            {collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
          </button>
        </div>

        <nav className="sidebar-nav">
          {visibleNav.map((item) => {
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                className={`nav-item-btn ${isActive ? 'active' : ''}`}
                onClick={() => onTabChange(item.id)}
                title={collapsed ? item.label : undefined}
              >
                <span className="nav-item-icon">{item.icon}</span>
                {!collapsed && <span className="nav-item-label">{item.label}</span>}
                {!collapsed && item.badge && <span className="nav-item-badge">{item.badge}</span>}
              </button>
            );
          })}
        </nav>

        <div className="sidebar-footer">
          {!collapsed && (
            <div className="safety-badge">
              <Shield size={12} />
              <span>Decision Support Only</span>
            </div>
          )}
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="layout-main-wrapper">
        {/* Top Navbar */}
        <header className="layout-topnav">
          <div className="topnav-left">
            <h2 className="current-page-title">
              {NAV_ITEMS.find((n) => n.id === activeTab)?.label || 'Overview'}
            </h2>
          </div>

          <div className="topnav-right">
            <div className="user-profile-badge">
              <div className="user-avatar">
                <UserIcon size={16} />
              </div>
              <div className="user-meta">
                <span className="user-name">{user?.fullName || user?.username}</span>
                <span className="user-role-tag">{user?.role}</span>
              </div>
            </div>

            <button className="btn btn-secondary btn-sm logout-btn" onClick={logout} title="Sign Out">
              <LogOut size={16} />
              <span className="logout-text">Sign Out</span>
            </button>
          </div>
        </header>

        {/* Dynamic Page Container */}
        <main className="layout-content-area">
          {children}
        </main>
      </div>
    </div>
  );
};
