-- =====================================================================
-- HelixFlow Enterprise Database Initialization DDL
-- Target: MySQL 8.0+ / InnoDB
-- Features: 
--   - Two-Way Clinical Loop (SOAP Note Output 1 & Patient Care Summary Output 2)
--   - Pessimistic Row Locking Indexes for Beds & Operating Theatres
--   - Real-Time Operational Synchronization & AI Audit Trails
-- =====================================================================

CREATE DATABASE IF NOT EXISTS helixflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE helixflow;

-- Disable foreign key checks during initialization
SET FOREIGN_KEY_CHECKS = 0;

-- 1. Users Table
DROP TABLE IF EXISTS users;
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL, -- 'ADMIN', 'DOCTOR', 'NURSE', 'PHARMACIST', 'RECEPTIONIST'
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role (role)
) ENGINE=InnoDB;

-- 2. User Sessions Table (Interceptors & Auth Tokens)
DROP TABLE IF EXISTS user_sessions;
CREATE TABLE user_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_sessions_token (token),
    INDEX idx_sessions_expires (expires_at)
) ENGINE=InnoDB;

-- 3. Synthetic Patients
DROP TABLE IF EXISTS patients;
CREATE TABLE patients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mrn VARCHAR(32) NOT NULL UNIQUE, -- Medical Record Number (e.g. 'SYN-84920')
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(20) NOT NULL,
    phone VARCHAR(20) NULL,
    emergency_contact VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 4. Visits / Encounters
DROP TABLE IF EXISTS visits;
CREATE TABLE visits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    visit_number VARCHAR(32) NOT NULL UNIQUE,
    visit_type VARCHAR(20) NOT NULL, -- 'EMERGENCY', 'INPATIENT', 'SURGICAL', 'OUTPATIENT'
    status VARCHAR(20) NOT NULL, -- 'TRIAGE_WAIT', 'IN_TREATMENT', 'ADMITTED', 'DISCHARGED'
    arrival_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    discharge_time TIMESTAMP NULL,
    chief_complaint TEXT NOT NULL,
    assigned_doctor_id BIGINT NULL,
    CONSTRAINT fk_visits_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE RESTRICT,
    CONSTRAINT fk_visits_doctor FOREIGN KEY (assigned_doctor_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_visits_status (status),
    INDEX idx_visits_arrival (arrival_time)
) ENGINE=InnoDB;

-- 5. Triage Assessments (ESI 1-5 & Vital Signs)
DROP TABLE IF EXISTS triage_assessments;
CREATE TABLE triage_assessments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id BIGINT NOT NULL,
    triaged_by BIGINT NOT NULL,
    esi_level INT NOT NULL, -- Emergency Severity Index: 1 (Immediate/Resus) to 5 (Non-urgent)
    heart_rate INT NULL,
    blood_pressure_sys INT NULL,
    blood_pressure_dia INT NULL,
    respiratory_rate INT NULL,
    temperature_celsius DECIMAL(4,1) NULL,
    o2_saturation INT NULL,
    consciousness_level VARCHAR(20) NOT NULL DEFAULT 'ALERT',
    triage_notes TEXT NULL,
    triage_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_triage_visit FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE CASCADE,
    CONSTRAINT fk_triage_user FOREIGN KEY (triaged_by) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_triage_esi (esi_level)
) ENGINE=InnoDB;

-- 6. Wards and Beds (with Pessimistic Locking Support)
DROP TABLE IF EXISTS wards;
CREATE TABLE wards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(50) NOT NULL,
    total_capacity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

DROP TABLE IF EXISTS beds;
CREATE TABLE beds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ward_id BIGINT NOT NULL,
    bed_number VARCHAR(20) NOT NULL,
    bed_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- 'AVAILABLE', 'OCCUPIED', 'CLEANING', 'MAINTENANCE'
    version BIGINT NOT NULL DEFAULT 0, -- Secondary optimistic check
    CONSTRAINT fk_beds_ward FOREIGN KEY (ward_id) REFERENCES wards(id) ON DELETE CASCADE,
    UNIQUE KEY uq_ward_bed (ward_id, bed_number),
    INDEX idx_beds_ward_status (ward_id, status), -- Crucial for fast SELECT ... FOR UPDATE queries
    INDEX idx_beds_status (status)
) ENGINE=InnoDB;

-- 7. Bed Admissions
DROP TABLE IF EXISTS bed_admissions;
CREATE TABLE bed_admissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bed_id BIGINT NOT NULL,
    visit_id BIGINT NOT NULL,
    admitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    discharged_at TIMESTAMP NULL,
    notes VARCHAR(255) NULL,
    CONSTRAINT fk_admit_bed FOREIGN KEY (bed_id) REFERENCES beds(id) ON DELETE RESTRICT,
    CONSTRAINT fk_admit_visit FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE RESTRICT,
    INDEX idx_admit_active (discharged_at)
) ENGINE=InnoDB;

-- 8. Staff and Shifts
DROP TABLE IF EXISTS staff;
CREATE TABLE staff (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    staff_code VARCHAR(20) NOT NULL UNIQUE,
    department VARCHAR(50) NOT NULL,
    specialty VARCHAR(50) NULL,
    qualification VARCHAR(50) NOT NULL,
    max_weekly_hours INT NOT NULL DEFAULT 40,
    CONSTRAINT fk_staff_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

DROP TABLE IF EXISTS shifts;
CREATE TABLE shifts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    staff_id BIGINT NOT NULL,
    ward_id BIGINT NULL,
    shift_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    shift_type VARCHAR(20) NOT NULL,
    role_in_shift VARCHAR(30) NOT NULL,
    is_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_shift_staff FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    CONSTRAINT fk_shift_ward FOREIGN KEY (ward_id) REFERENCES wards(id) ON DELETE SET NULL,
    INDEX idx_shift_calendar (shift_date, start_time)
) ENGINE=InnoDB;

-- 9. Operating Theatres & Bookings (with Locking & Conflict Indexes)
DROP TABLE IF EXISTS operating_theatres;
CREATE TABLE operating_theatres (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_number VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    cleaning_buffer_minutes INT NOT NULL DEFAULT 30
) ENGINE=InnoDB;

DROP TABLE IF EXISTS ot_bookings;
CREATE TABLE ot_bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    theatre_id BIGINT NOT NULL,
    visit_id BIGINT NOT NULL,
    lead_surgeon_id BIGINT NOT NULL,
    procedure_name VARCHAR(150) NOT NULL,
    scheduled_start TIMESTAMP NOT NULL,
    scheduled_end TIMESTAMP NOT NULL,
    actual_start TIMESTAMP NULL,
    actual_end TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED', -- 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
    notes TEXT NULL,
    CONSTRAINT fk_ot_theatre FOREIGN KEY (theatre_id) REFERENCES operating_theatres(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ot_visit FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ot_surgeon FOREIGN KEY (lead_surgeon_id) REFERENCES staff(id) ON DELETE RESTRICT,
    INDEX idx_ot_theatre_window (theatre_id, status, scheduled_start, scheduled_end),
    INDEX idx_ot_surgeon_window (lead_surgeon_id, status, scheduled_start, scheduled_end)
) ENGINE=InnoDB;

-- 10. Medications and Interaction Rules
DROP TABLE IF EXISTS medications;
CREATE TABLE medications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    generic_name VARCHAR(100) NOT NULL,
    brand_name VARCHAR(100) NULL,
    dosage_form VARCHAR(50) NOT NULL,
    strength VARCHAR(50) NOT NULL,
    INDEX idx_med_generic (generic_name)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS medication_interactions;
CREATE TABLE medication_interactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    primary_med_id BIGINT NOT NULL,
    interacting_med_id BIGINT NOT NULL,
    severity VARCHAR(20) NOT NULL, -- 'MILD', 'MODERATE', 'SEVERE_CONTRAINDICATED'
    clinical_effect TEXT NOT NULL,
    management_advice TEXT NOT NULL,
    CONSTRAINT fk_med_prim FOREIGN KEY (primary_med_id) REFERENCES medications(id) ON DELETE CASCADE,
    CONSTRAINT fk_med_sec FOREIGN KEY (interacting_med_id) REFERENCES medications(id) ON DELETE CASCADE,
    UNIQUE KEY uq_interaction_pair (primary_med_id, interacting_med_id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS medication_orders;
CREATE TABLE medication_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id BIGINT NOT NULL,
    medication_id BIGINT NOT NULL,
    prescribed_by BIGINT NOT NULL,
    dose VARCHAR(50) NOT NULL,
    route VARCHAR(30) NOT NULL,
    frequency VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'COMPLETED', 'CANCELLED'
    interaction_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    override_reason TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_med_order_visit FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE RESTRICT,
    CONSTRAINT fk_med_order_med FOREIGN KEY (medication_id) REFERENCES medications(id) ON DELETE RESTRICT,
    CONSTRAINT fk_med_order_user FOREIGN KEY (prescribed_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 11. Clinical Notes, Grok-2 SOAP (Output 1)
DROP TABLE IF EXISTS clinical_notes;
CREATE TABLE clinical_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    raw_text LONGTEXT NOT NULL,
    note_type VARCHAR(30) NOT NULL DEFAULT 'PROGRESS_NOTE',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- 'DRAFT', 'AI_PROCESSED', 'CLINICIAN_SIGNED'
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signed_at TIMESTAMP NULL,
    signed_by_id BIGINT NULL,
    CONSTRAINT fk_notes_visit FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notes_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notes_signed_by FOREIGN KEY (signed_by_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

DROP TABLE IF EXISTS soap_outputs;
CREATE TABLE soap_outputs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinical_note_id BIGINT NOT NULL UNIQUE,
    subjective_json JSON NOT NULL,
    objective_json JSON NOT NULL,
    assessment_json JSON NOT NULL,
    plan_json JSON NOT NULL,
    needs_clinician_review JSON NULL,
    model_version VARCHAR(50) NOT NULL,
    is_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    clinician_edited_json JSON NULL,
    patient_summary_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_soap_note FOREIGN KEY (clinical_note_id) REFERENCES clinical_notes(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 12. Output 2: Patient-Facing Discharge Summaries (Two-Way Clinical Loop)
DROP TABLE IF EXISTS patient_care_summaries;
CREATE TABLE patient_care_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinical_note_id BIGINT NOT NULL UNIQUE,
    visit_id BIGINT NOT NULL,
    simplified_diagnosis TEXT NOT NULL,
    medication_instructions JSON NOT NULL,
    home_care_instructions JSON NOT NULL,
    red_flag_warnings JSON NOT NULL,
    follow_up_instructions TEXT NOT NULL,
    signed_by_doctor_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_summary_note FOREIGN KEY (clinical_note_id) REFERENCES clinical_notes(id) ON DELETE CASCADE,
    CONSTRAINT fk_summary_visit FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE RESTRICT,
    INDEX idx_summary_visit (visit_id),
    INDEX idx_summary_created (created_at)
) ENGINE=InnoDB;

-- 13. AI Audit Logs
DROP TABLE IF EXISTS ai_audit_logs;
CREATE TABLE ai_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinical_note_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    prompt_tokens INT NOT NULL,
    completion_tokens INT NOT NULL,
    latency_ms BIGINT NOT NULL,
    status_code INT NOT NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_created (created_at),
    CONSTRAINT fk_audit_note FOREIGN KEY (clinical_note_id) REFERENCES clinical_notes(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 14. Outbreak Alerts and Syndromic Surveillance
DROP TABLE IF EXISTS outbreak_alerts;
CREATE TABLE outbreak_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    syndrome_cluster VARCHAR(100) NOT NULL,
    detected_count INT NOT NULL,
    baseline_threshold DECIMAL(6,2) NOT NULL,
    z_score DECIMAL(5,2) NOT NULL,
    alert_level VARCHAR(20) NOT NULL,
    sample_visit_ids JSON NOT NULL,
    is_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_outbreak_time (created_at)
) ENGINE=InnoDB;

-- 15. Operational Forecasts
DROP TABLE IF EXISTS operational_forecasts;
CREATE TABLE operational_forecasts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    forecast_type VARCHAR(50) NOT NULL,
    target_time TIMESTAMP NOT NULL,
    predicted_value DECIMAL(8,2) NOT NULL,
    confidence_lower DECIMAL(8,2) NOT NULL,
    confidence_upper DECIMAL(8,2) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_forecast_time (target_time)
) ENGINE=InnoDB;

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;
