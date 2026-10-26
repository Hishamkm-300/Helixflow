import React, { useState, useEffect } from 'react';
import {
  CalendarDays,
  Clock,
  UserCheck,
  AlertTriangle,
  Plus,
  RefreshCw,
  CheckCircle,
  AlertCircle,
  Activity,
  Filter,
  ShieldAlert,
} from 'lucide-react';
import { otApi } from '../../services/otApi';
import { staffApi } from '../../services/staffApi';
import {
  OtBookingDto,
  OtBookingRequest,
  OtRoomDto,
  ShiftAssignmentDto,
  ShiftDto,
  ShiftType,
  StaffDto,
} from '../../types';
import './OtStaffSchedulingScreen.css';

export const OtStaffSchedulingScreen: React.FC = () => {
  const [activeSubTab, setActiveSubTab] = useState<'ot' | 'staff'>('ot');
  const [loading, setLoading] = useState(true);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // OT State
  const [theatres, setTheatres] = useState<OtRoomDto[]>([]);
  const [bookings, setBookings] = useState<OtBookingDto[]>([]);
  const [selectedTheatreId, setSelectedTheatreId] = useState<number | null>(null);
  const [isBookingModalOpen, setIsBookingModalOpen] = useState(false);
  const [newBooking, setNewBooking] = useState<OtBookingRequest>({
    theatreId: 1,
    visitId: 1,
    leadSurgeonId: 2, // Dr. Sarah Adams
    procedureName: 'Exploratory Laparotomy & Hemostasis',
    scheduledStart: new Date(Date.now() + 3600000 * 2).toISOString().slice(0, 16),
    scheduledEnd: new Date(Date.now() + 3600000 * 4).toISOString().slice(0, 16),
    emergencyOverride: false,
    notes: 'Urgent general trauma procedure.',
  });

  // Staff Roster State
  const [staffList, setStaffList] = useState<StaffDto[]>([]);
  const [roster, setRoster] = useState<ShiftDto[]>([]);
  const [selectedDept, setSelectedDept] = useState<string>('ALL');
  const [isShiftModalOpen, setIsShiftModalOpen] = useState(false);
  const [newShift, setNewShift] = useState<ShiftAssignmentDto>({
    staffId: 1,
    wardId: 1,
    shiftDate: new Date().toISOString().slice(0, 10),
    startTime: '07:00',
    endTime: '15:00',
    shiftType: 'DAY',
    roleInShift: 'Staff Nurse',
  });

  const loadAllData = async () => {
    try {
      const [theatreData, bookingData, staffData, rosterData] = await Promise.all([
        otApi.getTheatres().catch(() => []),
        otApi.getBookings().catch(() => []),
        staffApi.getAllStaff().catch(() => []),
        staffApi.getRoster().catch(() => []),
      ]);
      setTheatres(theatreData);
      setBookings(bookingData);
      setStaffList(staffData);
      setRoster(rosterData);
    } catch (err) {
      console.error('Failed to load scheduling data', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAllData();
  }, []);

  const handleCreateBooking = async (e: React.FormEvent) => {
    e.preventDefault();
    setFeedback(null);
    try {
      const result = await otApi.createBooking(newBooking);
      setFeedback({
        type: 'success',
        text: `Surgery scheduled in ${result.roomNumber} for ${result.patientName} (${result.procedureName}). 30-min sanitation buffer locked.`,
      });
      setIsBookingModalOpen(false);
      loadAllData();
    } catch (err: any) {
      setFeedback({
        type: 'error',
        text: err.message || 'OT scheduling conflict detected.',
      });
    }
  };

  const handleCancelBooking = async (bookingId: number) => {
    const reason = prompt('Enter procedure cancellation reason:');
    if (!reason) return;
    try {
      await otApi.cancelBooking(bookingId, reason);
      setFeedback({ type: 'success', text: `Booking #${bookingId} cancelled.` });
      loadAllData();
    } catch (err: any) {
      setFeedback({ type: 'error', text: err.message || 'Failed to cancel procedure.' });
    }
  };

  const handleAssignShift = async (e: React.FormEvent) => {
    e.preventDefault();
    setFeedback(null);
    try {
      const shift = await staffApi.assignShift(newShift);
      setFeedback({
        type: 'success',
        text: `Shift successfully assigned to ${shift.staffName} on ${shift.shiftDate} (${shift.startTime}-${shift.endTime}).`,
      });
      setIsShiftModalOpen(false);
      loadAllData();
    } catch (err: any) {
      setFeedback({
        type: 'error',
        text: err.message || 'Shift assignment violated rest break or max weekly hour constraint.',
      });
    }
  };

  const filteredBookings = selectedTheatreId
    ? bookings.filter((b) => b.theatreId === selectedTheatreId)
    : bookings;

  const filteredRoster = selectedDept === 'ALL'
    ? roster
    : roster.filter((s) => {
        const staff = staffList.find((st) => st.id === s.staffId);
        return staff?.department === selectedDept;
      });

  return (
    <div className="scheduling-screen animate-fade-in">
      <div className="screen-header">
        <div>
          <h1>Operating Theatre & Staff Shift Logistics</h1>
          <p className="screen-subtitle">
            Pessimistic concurrency room reservations, surgeon collision buffers, and staff rest constraint verification
          </p>
        </div>
        <div className="tab-pill-group">
          <button
            className={`tab-pill ${activeSubTab === 'ot' ? 'active' : ''}`}
            onClick={() => setActiveSubTab('ot')}
          >
            <Activity size={16} />
            <span>Operating Theatres ({bookings.length})</span>
          </button>
          <button
            className={`tab-pill ${activeSubTab === 'staff' ? 'active' : ''}`}
            onClick={() => setActiveSubTab('staff')}
          >
            <UserCheck size={16} />
            <span>Staff Roster ({roster.length})</span>
          </button>
        </div>
      </div>

      {feedback && (
        <div className={`screen-alert ${feedback.type === 'success' ? 'alert-success' : 'alert-danger'}`}>
          {feedback.type === 'success' ? <CheckCircle size={18} /> : <AlertCircle size={18} />}
          <span>{feedback.text}</span>
        </div>
      )}

      {/* Sub-Tab 1: Operating Theatre */}
      {activeSubTab === 'ot' && (
        <div className="ot-subview">
          <div className="ot-top-bar card">
            <div className="theatre-selector-pills">
              <button
                className={`filter-btn ${selectedTheatreId === null ? 'active' : ''}`}
                onClick={() => setSelectedTheatreId(null)}
              >
                All Theatres ({theatres.length})
              </button>
              {theatres.map((t) => (
                <button
                  key={t.id}
                  className={`filter-btn ${selectedTheatreId === t.id ? 'active' : ''}`}
                  onClick={() => setSelectedTheatreId(t.id)}
                >
                  {t.roomNumber} &bull; {t.name}
                </button>
              ))}
            </div>

            <button className="btn btn-primary btn-sm" onClick={() => setIsBookingModalOpen(true)}>
              <Plus size={16} />
              <span>Schedule Surgery</span>
            </button>
          </div>

          <div className="card">
            <div className="card-header">
              <div className="card-title">
                <CalendarDays size={18} />
                <span>Operating Room Procedures & Reserved Buffers</span>
              </div>
              <span className="badge badge-ai">30-Min Sanitation Guard Active</span>
            </div>

            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Room</th>
                    <th>Procedure</th>
                    <th>Patient (Visit)</th>
                    <th>Lead Surgeon</th>
                    <th>Schedule Window</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredBookings.map((b) => (
                    <tr key={b.id}>
                      <td>
                        <strong className="mono">{b.roomNumber}</strong>
                        <div className="text-muted" style={{ fontSize: '0.75rem' }}>{b.theatreName}</div>
                      </td>
                      <td>
                        <strong>{b.procedureName}</strong>
                        {b.notes && <div className="text-muted" style={{ fontSize: '0.75rem' }}>{b.notes}</div>}
                      </td>
                      <td>
                        <div>{b.patientName}</div>
                        <div className="text-muted mono" style={{ fontSize: '0.75rem' }}>{b.visitNumber}</div>
                      </td>
                      <td>
                        <div className="text-secondary">{b.leadSurgeonName}</div>
                      </td>
                      <td className="mono" style={{ fontSize: '0.8rem' }}>
                        <div>{new Date(b.scheduledStart).toLocaleDateString()}</div>
                        <div className="text-muted">
                          {new Date(b.scheduledStart).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} -{' '}
                          {new Date(b.scheduledEnd).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </div>
                      </td>
                      <td>
                        <span
                          className={`badge ${
                            b.status === 'SCHEDULED'
                              ? 'badge-available'
                              : b.status === 'IN_PROGRESS'
                              ? 'badge-cleaning'
                              : b.status === 'CANCELLED'
                              ? 'badge-occupied'
                              : 'badge-maintenance'
                          }`}
                        >
                          {b.status}
                        </span>
                      </td>
                      <td>
                        {b.status === 'SCHEDULED' && (
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => handleCancelBooking(b.id)}
                          >
                            Cancel
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                  {filteredBookings.length === 0 && (
                    <tr>
                      <td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                        No surgical procedures scheduled for this selection.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Sub-Tab 2: Staff Roster */}
      {activeSubTab === 'staff' && (
        <div className="staff-subview">
          <div className="ot-top-bar card">
            <div className="theatre-selector-pills">
              <Filter size={16} />
              <span>Department:</span>
              {['ALL', 'EMERGENCY', 'SURGERY', 'ICU', 'GENERAL'].map((dept) => (
                <button
                  key={dept}
                  className={`filter-btn ${selectedDept === dept ? 'active' : ''}`}
                  onClick={() => setSelectedDept(dept)}
                >
                  {dept}
                </button>
              ))}
            </div>

            <button className="btn btn-primary btn-sm" onClick={() => setIsShiftModalOpen(true)}>
              <Plus size={16} />
              <span>Assign Shift</span>
            </button>
          </div>

          <div className="card">
            <div className="card-header">
              <div className="card-title">
                <UserCheck size={18} />
                <span>Active Hospital Clinical Shift Roster</span>
              </div>
              <span className="badge badge-ai">Mandatory 8h Break Guard Enforced</span>
            </div>

            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Staff Member</th>
                    <th>Code</th>
                    <th>Shift Date</th>
                    <th>Hours</th>
                    <th>Type</th>
                    <th>Assigned Ward</th>
                    <th>Role in Shift</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredRoster.map((s) => (
                    <tr key={s.id}>
                      <td><strong>{s.staffName}</strong></td>
                      <td className="mono text-muted">{s.staffCode}</td>
                      <td>{s.shiftDate}</td>
                      <td className="mono">{s.startTime} - {s.endTime}</td>
                      <td>
                        <span className="badge badge-ai">{s.shiftType}</span>
                      </td>
                      <td>{s.wardCode || 'Hospital-Wide'}</td>
                      <td>{s.roleInShift || 'Clinical Staff'}</td>
                      <td>
                        {s.cancelled ? (
                          <span className="badge badge-occupied">CANCELLED</span>
                        ) : (
                          <span className="badge badge-available">ACTIVE</span>
                        )}
                      </td>
                    </tr>
                  ))}
                  {filteredRoster.length === 0 && (
                    <tr>
                      <td colSpan={8} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                        No staff shifts found.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Schedule OT Modal */}
      {isBookingModalOpen && (
        <div className="modal-backdrop">
          <div className="modal-dialog">
            <div className="modal-header">
              <h3>Schedule Surgical Procedure</h3>
              <button className="btn btn-secondary btn-sm" onClick={() => setIsBookingModalOpen(false)}>
                &times;
              </button>
            </div>
            <form onSubmit={handleCreateBooking}>
              <div className="modal-body">
                <div className="disclaimer-banner">
                  <ShieldAlert size={16} />
                  <span>
                    Pessimistic locking validates room buffer (30m) & surgeon availability simultaneously.
                  </span>
                </div>

                <div className="form-group">
                  <label className="form-label">Operating Theatre *</label>
                  <select
                    value={newBooking.theatreId}
                    onChange={(e) => setNewBooking({ ...newBooking, theatreId: parseInt(e.target.value) })}
                    required
                  >
                    {theatres.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.roomNumber} - {t.name} (Buffer: {t.cleaningBufferMinutes}m)
                      </option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Procedure Name *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Laparoscopic Appendectomy"
                    value={newBooking.procedureName}
                    onChange={(e) => setNewBooking({ ...newBooking, procedureName: e.target.value })}
                  />
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Lead Surgeon *</label>
                    <select
                      value={newBooking.leadSurgeonId}
                      onChange={(e) => setNewBooking({ ...newBooking, leadSurgeonId: parseInt(e.target.value) })}
                      required
                    >
                      {staffList.filter(s => s.specialty?.toLowerCase().includes('surg') || s.department === 'SURGERY' || s.username.includes('adams') || s.username.includes('smith')).map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.fullName} ({s.specialty || s.department})
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Patient Visit ID *</label>
                    <input
                      type="number"
                      required
                      min={1}
                      value={newBooking.visitId}
                      onChange={(e) => setNewBooking({ ...newBooking, visitId: parseInt(e.target.value) || 1 })}
                    />
                  </div>
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Scheduled Start *</label>
                    <input
                      type="datetime-local"
                      required
                      value={newBooking.scheduledStart}
                      onChange={(e) => setNewBooking({ ...newBooking, scheduledStart: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Scheduled End *</label>
                    <input
                      type="datetime-local"
                      required
                      value={newBooking.scheduledEnd}
                      onChange={(e) => setNewBooking({ ...newBooking, scheduledEnd: e.target.value })}
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      style={{ width: 'auto' }}
                      checked={newBooking.emergencyOverride || false}
                      onChange={(e) => setNewBooking({ ...newBooking, emergencyOverride: e.target.checked })}
                    />
                    <span style={{ fontSize: '0.88rem', color: '#fca5a5', fontWeight: 600 }}>
                      Emergency Trauma Override (Preempts active elective room reservation)
                    </span>
                  </label>
                </div>
              </div>

              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setIsBookingModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Reserve Operating Theatre
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Assign Shift Modal */}
      {isShiftModalOpen && (
        <div className="modal-backdrop">
          <div className="modal-dialog">
            <div className="modal-header">
              <h3>Assign Clinical Staff Shift</h3>
              <button className="btn btn-secondary btn-sm" onClick={() => setIsShiftModalOpen(false)}>
                &times;
              </button>
            </div>
            <form onSubmit={handleAssignShift}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Staff Clinician *</label>
                  <select
                    value={newShift.staffId}
                    onChange={(e) => setNewShift({ ...newShift, staffId: parseInt(e.target.value) })}
                    required
                  >
                    {staffList.map((st) => (
                      <option key={st.id} value={st.id}>
                        {st.fullName} ({st.staffCode} - {st.department}) &bull; Max: {st.maxWeeklyHours}h/wk
                      </option>
                    ))}
                  </select>
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Shift Date *</label>
                    <input
                      type="date"
                      required
                      value={newShift.shiftDate}
                      onChange={(e) => setNewShift({ ...newShift, shiftDate: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Shift Type *</label>
                    <select
                      value={newShift.shiftType}
                      onChange={(e) => setNewShift({ ...newShift, shiftType: e.target.value as ShiftType })}
                    >
                      <option value="DAY">DAY (Morning)</option>
                      <option value="EVENING">EVENING (Swing)</option>
                      <option value="NIGHT">NIGHT (Graveyard)</option>
                      <option value="ON_CALL">ON CALL (Rapid Response)</option>
                    </select>
                  </div>
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Start Time *</label>
                    <input
                      type="time"
                      required
                      value={newShift.startTime}
                      onChange={(e) => setNewShift({ ...newShift, startTime: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">End Time *</label>
                    <input
                      type="time"
                      required
                      value={newShift.endTime}
                      onChange={(e) => setNewShift({ ...newShift, endTime: e.target.value })}
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Role in Shift</label>
                  <input
                    type="text"
                    placeholder="e.g. Lead Resuscitation Nurse"
                    value={newShift.roleInShift || ''}
                    onChange={(e) => setNewShift({ ...newShift, roleInShift: e.target.value })}
                  />
                </div>
              </div>

              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setIsShiftModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Validate & Assign Shift
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
