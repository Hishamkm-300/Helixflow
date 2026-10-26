package com.helixflow.common.bootstrap;

import com.helixflow.modules.auth.entity.User;
import com.helixflow.modules.auth.repository.UserRepository;
import com.helixflow.modules.patient.entity.*;
import com.helixflow.modules.patient.repository.PatientRepository;
import com.helixflow.modules.patient.repository.TriageAssessmentRepository;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.modules.resource.bed.entity.*;
import com.helixflow.modules.resource.bed.repository.BedAdmissionRepository;
import com.helixflow.modules.resource.bed.repository.BedRepository;
import com.helixflow.modules.resource.bed.repository.WardRepository;
import com.helixflow.modules.resource.medication.entity.*;
import com.helixflow.modules.resource.medication.repository.MedicationInteractionRepository;
import com.helixflow.modules.resource.medication.repository.MedicationOrderRepository;
import com.helixflow.modules.resource.medication.repository.MedicationRepository;
import com.helixflow.modules.resource.ot.entity.OperatingTheatre;
import com.helixflow.modules.resource.ot.entity.OtBooking;
import com.helixflow.modules.resource.ot.entity.OtBookingStatus;
import com.helixflow.modules.resource.ot.repository.OperatingTheatreRepository;
import com.helixflow.modules.resource.ot.repository.OtBookingRepository;
import com.helixflow.modules.resource.staff.entity.Staff;
import com.helixflow.modules.resource.staff.repository.StaffRepository;
import com.helixflow.security.PasswordService;
import com.helixflow.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseBootstrapper implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final WardRepository wardRepository;
    private final BedRepository bedRepository;
    private final BedAdmissionRepository admissionRepository;
    private final OperatingTheatreRepository theatreRepository;
    private final OtBookingRepository otBookingRepository;
    private final MedicationRepository medicationRepository;
    private final MedicationInteractionRepository interactionRepository;
    private final MedicationOrderRepository medicationOrderRepository;
    private final PatientRepository patientRepository;
    private final VisitRepository visitRepository;
    private final TriageAssessmentRepository triageRepository;
    private final PasswordService passwordService;

    @Override
    @Transactional
    public void run(String... args) {
        if (patientRepository.count() >= 50) {
            log.info("Database already seeded with synthetic operational data. Skipping bootstrap.");
            return;
        }

        log.info("Beginning comprehensive HelixFlow database bootstrap...");

        // 1. Seed Users
        Map<String, User> users = seedUsers();

        // 2. Seed Staff
        Map<String, Staff> staff = seedStaff(users);

        // 3. Seed Wards & Beds
        Map<String, Ward> wards = seedWards();
        List<Bed> beds = seedBeds(wards);

        // 4. Seed Operating Theatres
        Map<String, OperatingTheatre> theatres = seedTheatres();

        // 5. Seed Formulary Medications & Pairwise Interaction Rules
        Map<String, Medication> meds = seedMedications();
        seedInteractionRules(meds);

        // 6. Seed 50+ Synthetic Patients, Encounters, and Triage Records
        seedSyntheticPatientsAndEncounters(users, beds, theatres, staff, meds);

        log.info("HelixFlow database bootstrap completed successfully.");
    }

    private Map<String, User> seedUsers() {
        Map<String, User> userMap = new HashMap<>();
        userMap.put("admin", getOrCreateUser("admin", "admin@helixflow.org", "AdminPass123!", "System Administrator", Role.ADMIN));
        userMap.put("dr_smith", getOrCreateUser("dr_smith", "smith@helixflow.org", "DoctorPass123!", "Dr. Robert Smith, MD", Role.DOCTOR));
        userMap.put("dr_adams", getOrCreateUser("dr_adams", "adams@helixflow.org", "SurgeonPass123!", "Dr. Sarah Adams, FACS", Role.DOCTOR));
        userMap.put("nurse_joy", getOrCreateUser("nurse_joy", "joy@helixflow.org", "NursePass123!", "Nurse Joy Patel, BSN, RN", Role.NURSE));
        userMap.put("pharmacist_dan", getOrCreateUser("pharmacist_dan", "dan@helixflow.org", "PharmPass123!", "Dan Chen, PharmD", Role.PHARMACIST));
        userMap.put("rec_anna", getOrCreateUser("rec_anna", "anna@helixflow.org", "ReceptPass123!", "Anna Kowalski, Patient Intake", Role.RECEPTIONIST));
        return userMap;
    }

    private User getOrCreateUser(String username, String email, String password, String fullName, Role role) {
        return userRepository.findByUsername(username).orElseGet(() ->
                userRepository.save(User.builder()
                        .username(username)
                        .email(email)
                        .passwordHash(passwordService.hash(password))
                        .fullName(fullName)
                        .role(role)
                        .active(true)
                        .build())
        );
    }

    private Map<String, Staff> seedStaff(Map<String, User> users) {
        Map<String, Staff> staffMap = new HashMap<>();

        if (staffRepository.count() == 0) {
            Staff s1 = staffRepository.save(Staff.builder()
                    .user(users.get("dr_smith"))
                    .staffCode("STF-DOC-01")
                    .department("EMERGENCY")
                    .specialty("Emergency Medicine")
                    .qualification("MD, FACEP")
                    .maxWeeklyHours(48)
                    .build());
            staffMap.put("dr_smith", s1);

            Staff s2 = staffRepository.save(Staff.builder()
                    .user(users.get("dr_adams"))
                    .staffCode("STF-SURG-01")
                    .department("SURGERY")
                    .specialty("Trauma & General Surgery")
                    .qualification("MD, FACS")
                    .maxWeeklyHours(48)
                    .build());
            staffMap.put("dr_adams", s2);

            Staff s3 = staffRepository.save(Staff.builder()
                    .user(users.get("nurse_joy"))
                    .staffCode("STF-NUR-01")
                    .department("EMERGENCY")
                    .specialty("Triage & Resuscitation")
                    .qualification("BSN, RN, CEN")
                    .maxWeeklyHours(40)
                    .build());
            staffMap.put("nurse_joy", s3);
        } else {
            staffRepository.findAll().forEach(s -> staffMap.put(s.getUser().getUsername(), s));
        }
        return staffMap;
    }

    private Map<String, Ward> seedWards() {
        Map<String, Ward> wardMap = new HashMap<>();
        wardMap.put("ED-ACUTE", wardRepository.findByCode("ED-ACUTE").orElseGet(() ->
                wardRepository.save(Ward.builder().code("ED-ACUTE").name("Emergency Acute Care").department("EMERGENCY").totalCapacity(10).build())));
        wardMap.put("ICU-MAIN", wardRepository.findByCode("ICU-MAIN").orElseGet(() ->
                wardRepository.save(Ward.builder().code("ICU-MAIN").name("Intensive Care Unit").department("ICU").totalCapacity(8).build())));
        wardMap.put("MED-SURG-A", wardRepository.findByCode("MED-SURG-A").orElseGet(() ->
                wardRepository.save(Ward.builder().code("MED-SURG-A").name("Medical-Surgical Ward A").department("GENERAL").totalCapacity(12).build())));
        wardMap.put("GENERAL-WARD", wardRepository.findByCode("GENERAL-WARD").orElseGet(() ->
                wardRepository.save(Ward.builder().code("GENERAL-WARD").name("General Medical Ward").department("GENERAL").totalCapacity(10).build())));
        return wardMap;
    }

    private List<Bed> seedBeds(Map<String, Ward> wards) {
        if (bedRepository.count() >= 40) {
            return bedRepository.findAll();
        }
        List<Bed> beds = new ArrayList<>();
        Ward ed = wards.get("ED-ACUTE");
        for (int i = 1; i <= 10; i++) {
            BedStatus status = BedStatus.AVAILABLE;
            if (i >= 2 && i <= 4) status = BedStatus.OCCUPIED;
            else if (i == 5) status = BedStatus.CLEANING;
            beds.add(bedRepository.save(Bed.builder().ward(ed).bedNumber("ED-" + String.format("%02d", i)).bedType(BedType.MONITORED).status(status).build()));
        }
        Ward icu = wards.get("ICU-MAIN");
        for (int i = 1; i <= 8; i++) {
            BedStatus status = BedStatus.AVAILABLE;
            if (i <= 2) status = BedStatus.OCCUPIED;
            else if (i == 3) status = BedStatus.CLEANING;
            beds.add(bedRepository.save(Bed.builder().ward(icu).bedNumber("ICU-" + String.format("%02d", i)).bedType(BedType.ICU_VENTILATOR).status(status).build()));
        }
        Ward ms = wards.get("MED-SURG-A");
        for (int i = 1; i <= 12; i++) {
            BedStatus status = BedStatus.AVAILABLE;
            if (i <= 3) status = BedStatus.OCCUPIED;
            else if (i == 4) status = BedStatus.CLEANING;
            beds.add(bedRepository.save(Bed.builder().ward(ms).bedNumber("MS-" + String.format("%02d", i)).bedType(BedType.STANDARD).status(status).build()));
        }
        Ward gw = wards.get("GENERAL-WARD");
        for (int i = 1; i <= 10; i++) {
            BedStatus status = BedStatus.AVAILABLE;
            if (i <= 2) status = BedStatus.OCCUPIED;
            else if (i == 3) status = BedStatus.CLEANING;
            beds.add(bedRepository.save(Bed.builder().ward(gw).bedNumber("GW-" + String.format("%02d", i)).bedType(BedType.STANDARD).status(status).build()));
        }
        return beds;
    }

    private Map<String, OperatingTheatre> seedTheatres() {
        Map<String, OperatingTheatre> map = new HashMap<>();
        if (theatreRepository.count() == 0) {
            map.put("OT-01", theatreRepository.save(OperatingTheatre.builder().roomNumber("OT-01").name("General Surgery Suite").active(true).cleaningBufferMinutes(30).build()));
            map.put("OT-02", theatreRepository.save(OperatingTheatre.builder().roomNumber("OT-02").name("Trauma & Orthopedic OR").active(true).cleaningBufferMinutes(30).build()));
            map.put("OT-03", theatreRepository.save(OperatingTheatre.builder().roomNumber("OT-03").name("Cardiovascular Hybrid Suite").active(true).cleaningBufferMinutes(45).build()));
        } else {
            theatreRepository.findAll().forEach(t -> map.put(t.getRoomNumber(), t));
        }
        return map;
    }

    private Map<String, Medication> seedMedications() {
        Map<String, Medication> map = new HashMap<>();
        if (medicationRepository.count() == 0) {
            map.put("WARFARIN", medicationRepository.save(Medication.builder().code("RX-WARFARIN").genericName("Warfarin Sodium").brandName("Coumadin").dosageForm("TABLET").strength("5mg").maxSingleDoseMg(20.0).unit("mg").build()));
            map.put("ASPIRIN", medicationRepository.save(Medication.builder().code("RX-ASPIRIN").genericName("Aspirin").brandName("Bayer").dosageForm("TABLET").strength("81mg").maxSingleDoseMg(1000.0).unit("mg").build()));
            map.put("NITRO", medicationRepository.save(Medication.builder().code("RX-NITRO").genericName("Nitroglycerin").brandName("Nitrostat").dosageForm("SUBLINGUAL").strength("0.4mg").maxSingleDoseMg(1.2).unit("mg").build()));
            map.put("SILDENAFIL", medicationRepository.save(Medication.builder().code("RX-SILDENAFIL").genericName("Sildenafil").brandName("Viagra / Revatio").dosageForm("TABLET").strength("50mg").maxSingleDoseMg(100.0).unit("mg").build()));
            map.put("LISINOPRIL", medicationRepository.save(Medication.builder().code("RX-LISINOPRIL").genericName("Lisinopril").brandName("Zestril").dosageForm("TABLET").strength("10mg").maxSingleDoseMg(80.0).unit("mg").build()));
            map.put("POTASSIUM", medicationRepository.save(Medication.builder().code("RX-KCL").genericName("Potassium Chloride").brandName("K-Dur").dosageForm("TABLET").strength("20mEq").maxSingleDoseMg(40.0).unit("mEq").build()));
            map.put("CLOPIDOGREL", medicationRepository.save(Medication.builder().code("RX-PLAVIX").genericName("Clopidogrel").brandName("Plavix").dosageForm("TABLET").strength("75mg").maxSingleDoseMg(300.0).unit("mg").build()));
            map.put("IBUPROFEN", medicationRepository.save(Medication.builder().code("RX-IBUPROFEN").genericName("Ibuprofen").brandName("Advil / Motrin").dosageForm("TABLET").strength("400mg").maxSingleDoseMg(800.0).unit("mg").build()));
            map.put("METFORMIN", medicationRepository.save(Medication.builder().code("RX-METFORMIN").genericName("Metformin HCl").brandName("Glucophage").dosageForm("TABLET").strength("500mg").maxSingleDoseMg(2000.0).unit("mg").build()));
            map.put("MORPHINE", medicationRepository.save(Medication.builder().code("RX-MORPHINE").genericName("Morphine Sulfate").brandName("MS Contin").dosageForm("IV_INJECTION").strength("4mg/ml").maxSingleDoseMg(15.0).unit("mg").build()));
        } else {
            medicationRepository.findAll().forEach(m -> {
                boolean updated = false;
                if (m.getMaxSingleDoseMg() == null) {
                    if ("RX-WARFARIN".equals(m.getCode())) { m.setMaxSingleDoseMg(20.0); m.setUnit("mg"); updated = true; }
                    else if ("RX-ASPIRIN".equals(m.getCode())) { m.setMaxSingleDoseMg(1000.0); m.setUnit("mg"); updated = true; }
                    else if ("RX-NITRO".equals(m.getCode())) { m.setMaxSingleDoseMg(1.2); m.setUnit("mg"); updated = true; }
                    else if ("RX-SILDENAFIL".equals(m.getCode())) { m.setMaxSingleDoseMg(100.0); m.setUnit("mg"); updated = true; }
                    else if ("RX-LISINOPRIL".equals(m.getCode())) { m.setMaxSingleDoseMg(80.0); m.setUnit("mg"); updated = true; }
                    else if ("RX-KCL".equals(m.getCode())) { m.setMaxSingleDoseMg(40.0); m.setUnit("mEq"); updated = true; }
                    else if ("RX-PLAVIX".equals(m.getCode())) { m.setMaxSingleDoseMg(300.0); m.setUnit("mg"); updated = true; }
                    else if ("RX-IBUPROFEN".equals(m.getCode())) { m.setMaxSingleDoseMg(800.0); m.setUnit("mg"); updated = true; }
                    else if ("RX-METFORMIN".equals(m.getCode())) { m.setMaxSingleDoseMg(2000.0); m.setUnit("mg"); updated = true; }
                    else if ("RX-MORPHINE".equals(m.getCode())) { m.setMaxSingleDoseMg(15.0); m.setUnit("mg"); updated = true; }
                    if (updated) {
                        m = medicationRepository.save(m);
                    }
                }
                map.put(m.getCode().replace("RX-", ""), m);
            });
        }
        return map;
    }

    private void seedInteractionRules(Map<String, Medication> meds) {
        if (interactionRepository.count() > 0) return;

        createRule(meds.get("WARFARIN"), meds.get("ASPIRIN"), InteractionSeverity.SEVERE_CONTRAINDICATED,
                "Synergistic anticoagulant/antiplatelet effect exponentially increases risk of major gastrointestinal and intracranial hemorrhage.",
                "Avoid concurrent therapy unless mechanical heart valve protocol explicitly indicated. Monitor INR closely.");

        createRule(meds.get("NITRO"), meds.get("SILDENAFIL"), InteractionSeverity.SEVERE_CONTRAINDICATED,
                "PDE-5 inhibitors potentiate the hypotensive effects of organic nitrates, risking profound cardiovascular collapse and death.",
                "Absolute contraindication. Do not administer nitrates within 24-48 hours of PDE-5 inhibitor use.");

        createRule(meds.get("LISINOPRIL"), meds.get("POTASSIUM"), InteractionSeverity.MODERATE,
                "ACE inhibitors decrease aldosterone secretion, promoting potassium retention and predisposing patient to life-threatening hyperkalemia.",
                "Monitor baseline serum creatinine and potassium levels within 72 hours of co-administration.");

        createRule(meds.get("ASPIRIN"), meds.get("IBUPROFEN"), InteractionSeverity.MODERATE,
                "NSAIDs competitively inhibit platelet COX-1 binding, impairing the irreversible cardioprotective antiplatelet effect of low-dose aspirin.",
                "Take immediate-release aspirin at least 30 minutes before or 8 hours after ibuprofen.");

        createRule(meds.get("MORPHINE"), meds.get("CLOPIDOGREL"), InteractionSeverity.MILD,
                "Parenteral opioids delay gastric emptying, attenuating and delaying clopidogrel absorption and peak antiplatelet activity.",
                "Consider alternative antiplatelet (ticagrelor/prasugrel) or non-opioid analgesics in acute coronary syndrome.");
    }

    private void createRule(Medication m1, Medication m2, InteractionSeverity severity, String effect, String advice) {
        if (m1 != null && m2 != null) {
            interactionRepository.save(MedicationInteractionRule.builder()
                    .primaryMed(m1)
                    .interactingMed(m2)
                    .severity(severity)
                    .clinicalEffect(effect)
                    .managementAdvice(advice)
                    .build());
        }
    }

    private void seedSyntheticPatientsAndEncounters(
            Map<String, User> users, List<Bed> beds, Map<String, OperatingTheatre> theatres,
            Map<String, Staff> staff, Map<String, Medication> meds) {

        String[] firstNames = {"James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda", "William", "Elizabeth",
                "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica", "Thomas", "Sarah", "Charles", "Karen",
                "Christopher", "Nancy", "Daniel", "Lisa", "Matthew", "Betty", "Anthony", "Margaret", "Donald", "Sandra",
                "Mark", "Ashley", "Paul", "Kimberly", "Steven", "Emily", "Andrew", "Donna", "Kenneth", "Michelle",
                "Joshua", "Dorothy", "Kevin", "Carol", "Brian", "Amanda", "George", "Melissa", "Edward", "Deborah",
                "Ronald", "Stephanie", "Timothy", "Rebecca", "Jason", "Sharon"};

        String[] lastNames = {"Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
                "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin",
                "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson",
                "Walker", "Young", "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores",
                "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell", "Mitchell", "Carter", "Roberts"};

        // Chief complaints taxonomy - includes respiratory cluster to test Sentinel!
        String[] complaints = {
                "Sudden sharp retrosternal chest pain radiating to left jaw, diaphoresis",
                "High grade fever (39.2C), persistent dry cough, progressive shortness of breath",
                "Severe dyspnea, wheezing, productive cough, febrile for 4 days",
                "Fever, sore throat, severe fatigue, body aches, chills",
                "Acute diffuse abdominal pain, bilious vomiting, inability to tolerate liquids",
                "Motor vehicle collision, right femoral deformity, blunt abdominal trauma",
                "Sudden onset right-sided facial droop and right arm motor weakness, speech slurred",
                "Persistent high fever, severe cough, pleuritic chest tightness",
                "Deep 7cm forearm laceration with active arterial pulsatile bleeding",
                "Intractable headache, neck stiffness, photophobia, fever"
        };

        User nurse = users.get("nurse_joy");
        User doctor = users.get("dr_smith");
        Staff surgeon = staff.get("dr_adams");
        OperatingTheatre ot1 = theatres.get("OT-01");

        Instant now = Instant.now();
        // Start from index 1 so ED-01 (index 0) remains available for immediate allocation and test workflows
        int bedAllocIndex = 1;

        for (int i = 0; i < 55; i++) {
            String fn = firstNames[i % firstNames.length];
            String ln = lastNames[i % lastNames.length];
            LocalDate dob = LocalDate.of(1950 + (i * 3) % 52, 1 + (i % 12), 1 + (i % 27));
            String gender = (i % 2 == 0) ? "MALE" : "FEMALE";
            String mrn = "SYN-" + (100000 + i);

            Patient patient = patientRepository.save(Patient.builder()
                    .mrn(mrn)
                    .firstName(fn)
                    .lastName(ln)
                    .dateOfBirth(dob)
                    .gender(gender)
                    .phone("555-" + String.format("%04d", 1000 + i))
                    .emergencyContact("Kin: " + ln + " (555-9999)")
                    .createdAt(now.minus(48 - (i % 48), ChronoUnit.HOURS))
                    .build());

            Instant arrival = now.minus((i * 45L) % (48 * 60), ChronoUnit.MINUTES);
            String complaint = complaints[i % complaints.length];
            VisitStatus status;

            if (i < 8) {
                status = VisitStatus.ADMITTED;
            } else if (i < 20) {
                status = VisitStatus.IN_TREATMENT;
            } else if (i < 35) {
                status = VisitStatus.TRIAGE_WAIT;
            } else {
                status = VisitStatus.DISCHARGED;
            }

            Visit visit = visitRepository.save(Visit.builder()
                    .patient(patient)
                    .visitNumber("ENC-" + (100000 + i))
                    .visitType(VisitType.EMERGENCY)
                    .status(status)
                    .arrivalTime(arrival)
                    .dischargeTime(status == VisitStatus.DISCHARGED ? arrival.plus(4, ChronoUnit.HOURS) : null)
                    .chiefComplaint(complaint)
                    .assignedDoctor(doctor)
                    .build());

            // Triage Assessment
            int esi = (i % 5 == 0) ? 1 : (i % 4 == 0) ? 2 : (i % 3 == 0) ? 3 : (i % 2 == 0) ? 4 : 5;
            int pain = (esi == 1) ? 9 : (esi == 2) ? 8 : (esi == 3) ? 6 : (esi == 4) ? 4 : 2;
            String duration = (i % 4 == 0) ? "45 minutes" : (i % 3 == 0) ? "2 to 3 hours" : (i % 2 == 0) ? "1 day" : "4 days";
            String nurseNotes = "Patient arrived via QR self-service check-in kiosk. Self-reports: " + complaint
                    + ". Triage vitals recorded by " + nurse.getFullName() + ". ESI-" + esi + " assigned.";

            triageRepository.save(TriageAssessment.builder()
                    .visit(visit)
                    .triagedBy(nurse)
                    .esiLevel(esi)
                    .heartRate(65 + (i * 7) % 55)
                    .bloodPressureSys(110 + (i * 5) % 60)
                    .bloodPressureDia(70 + (i * 3) % 30)
                    .respiratoryRate(14 + (i % 12))
                    .temperatureCelsius(36.6 + ((i % 10) * 0.3))
                    .o2Saturation(92 + (i % 8))
                    .consciousnessLevel(esi == 1 ? "UNRESPONSIVE" : "ALERT")
                    .triageNotes(nurseNotes)
                    .painScore(pain)
                    .symptomDuration(duration)
                    .triageTime(arrival.plus(5, ChronoUnit.MINUTES))
                    .build());

            // Allocate some beds
            if (status == VisitStatus.ADMITTED && bedAllocIndex < beds.size()) {
                Bed targetBed = beds.get(bedAllocIndex++);
                targetBed.setStatus(BedStatus.OCCUPIED);
                bedRepository.save(targetBed);

                admissionRepository.save(BedAdmission.builder()
                        .bed(targetBed)
                        .visit(visit)
                        .admittedAt(arrival.plus(30, ChronoUnit.MINUTES))
                        .notes("Admitted from ED triage")
                        .build());

                // Prescribe medication for admitted patient
                if (meds.containsKey("LISINOPRIL")) {
                    medicationOrderRepository.save(MedicationOrder.builder()
                            .visit(visit)
                            .medication(meds.get("LISINOPRIL"))
                            .prescribedBy(doctor)
                            .dose("10mg")
                            .route("ORAL")
                            .frequency("DAILY")
                            .status(OrderStatus.ACTIVE)
                            .createdAt(Instant.now())
                            .build());
                }
            }
        }

        // Schedule an OT procedure in 2 hours
        if (ot1 != null && surgeon != null) {
            Visit surgicalVisit = visitRepository.findAll().get(0);
            otBookingRepository.save(OtBooking.builder()
                    .theatre(ot1)
                    .visit(surgicalVisit)
                    .leadSurgeon(surgeon)
                    .procedureName("Exploratory Laparotomy & Hemostasis")
                    .scheduledStart(LocalDateTime.now().plusHours(2))
                    .scheduledEnd(LocalDateTime.now().plusHours(4))
                    .status(OtBookingStatus.SCHEDULED)
                    .notes("Urgent surgical intervention.")
                    .build());
        }

        log.info("55 synthetic patients, encounters, triage evaluations, and medication orders successfully generated.");
    }
}
