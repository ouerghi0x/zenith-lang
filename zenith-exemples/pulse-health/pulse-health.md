# PulseHealth — Digital Healthcare & Remote Patient Monitoring Platform

> **Zenith Enterprise Showcase Architecture & Project Blueprint**  
> Clinical electronic health records (EHR), practitioner workflows, biometric telemetry monitoring, and automated medical reminders built entirely with Zenith DSL.

---

## 📋 Executive Summary

**PulseHealth** is an enterprise-grade digital health and clinical management platform designed to model complex medical domain logic, regulatory access controls, and real-time biometric telemetry tracking using **Zenith DSL**.

Unlike standard CRUD web apps, digital health applications require:
* **Strict HIPAA/GDPR Role Separation**: Fine-grained authorization distinctions between Attending Physicians, Registered Nurses, Clinic Administrators, and Patients.
* **Clinical State Machines**: Appointment lifecycle transitions governed by strict business preconditions (guards) preventing invalid clinical operations.
* **High-Precision Biometric Tracking**: Telemetry records (Heart Rate, Systolic/Diastolic Blood Pressure, Oxygen Saturation, Blood Glucose, Temperature) with severity classification.
* **Secure Medical Document Vault**: Pathology lab reports (PDFs) and radiology scans (DICOM/PNG images) stored via S3/MinIO abstractions with signed access URLs.
* **Automated Preventive Workflows**: Scheduled background cron workers analyzing missed appointments, sending 24-hour consultation reminders, and flagging critical vital readings.

---

## 🏗 System Architecture & Topology

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        Zenith Web DSL Engine                           │
│               pulse-health.md / Modular Schema Definitions             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    Generated Spring Boot 3 Backend                     │
├───────────────────┬────────────────────┬───────────────────────────────┤
│ Security & RBAC   │ Clinical Workflows │ Biometrics & Telemetry        │
│ • JWT Tokens      │ • Appointments     │ • Vitals Log (BPM, SpO2)      │
│ • Doctor/Nurse/   │ • Medical Notes    │ • Anomaly Detection           │
│   Patient Roles   │ • Lab Reports (S3) │ • Severity Classification     │
├───────────────────┴────────────────────┴───────────────────────────────┤
│ Data Layer & Infrastructure                                            │
│ • PostgreSQL 15 + Flyway Migrations                                    │
│ • In-Memory H2 Isolated Testing Profile                                │
│ • MinIO / AWS S3 Medical Document Vault                                │
│ • Nightly Spring Task Scheduler (Reminders & Vitals Audits)             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   Full-Stack Client & Deployment                       │
│ • Strict TypeScript Medical SDK (`pulse-health-client`)                │
│ • Production Multi-Stage Docker & docker-compose Stacks                │
│ • Automated MockMvc & DataJpa Test Suites                              │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🗂 Modular Schema Organization

The PulseHealth architecture is decomposed into focused `.zen` modules that compile into a unified Spring Boot 3 enterprise application:

```text
zenith-exemples/pulse-health/
├── pulse-health.md      # Architectural blueprint, domain model, and reference specs
├── config.zen           # Server port, PostgreSQL dialect, JWT auth, and S3 medical bucket
├── auth.zen             # Practitioner/Patient user accounts, RBAC enums, and credentials
├── clinical.zen         # Patient profiles, medical appointments, and clinical consults
├── vitals.zen           # Biometric measurements, threshold alerts, and telemetry logs
├── prescriptions.zen    # Medication orders, dosage instructions, and pharmacy fulfillments
├── jobs.zen             # Scheduled cron workers for reminders and clinical audits
├── seeds.zen            # Relational demo dataset (doctors, patients, and initial vitals)
└── schema.zen           # Unified root entrypoint importing all modules
```

---

## 📄 Complete Modular Schema Specifications

### 1. Application Configuration (`config.zen`)

Declares global deployment parameters, PostgreSQL connectivity, Flyway automated migrations, JWT stateless authentication, and an isolated S3/MinIO bucket for HIPAA-sensitive files:

```zenith
config {
    app: "PulseHealth"
    package: "com.pulsehealth.api"
    basePath: "/api/v1"
    port: 8081
    database: postgres
    auth: jwt
    swagger: true
    flyway: true
    storage: s3
    storageBucket: "pulse-medical-vault"
}
```

---

### 2. Identity & Access Management (`auth.zen`)

Defines healthcare roles and credential models. The `@role` decorator binds directly to Spring Security 6 authorities (`hasRole('ROLE_DOCTOR')`):

```zenith
enum MedicalRole {
    ADMIN,
    DOCTOR,
    NURSE,
    PATIENT
}

enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    PENDING_VERIFICATION
}

model UserAccount {
    id: UUID @id @auto
    email: String @email @unique
    password: String @password
    role: MedicalRole = PATIENT @role
    status: AccountStatus = ACTIVE
    createdAt: DateTime @autoNow
    updatedAt: DateTime @autoUpdate

    relations {
        doctorProfile: DoctorProfile? @relation(oneToOne, mappedBy = "account")
        patientProfile: PatientProfile? @relation(oneToOne, mappedBy = "account")
    }

    endpoints {
        crud {
            paginate: true
            search: [email]
            filter: [role, status]
            sort: [createdAt]
        }
    }
}
```

---

### 3. Practitioner & Patient Clinical Records (`clinical.zen`)

Encompasses patient demographics, attending doctor profiles, and appointment lifecycle state machines:

```zenith
enum BloodType {
    A_POS,
    A_NEG,
    B_POS,
    B_NEG,
    AB_POS,
    AB_NEG,
    O_POS,
    O_NEG,
    UNKNOWN
}

enum AppointmentStatus {
    SCHEDULED,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}

model DoctorProfile {
    id: Long @id @auto
    licenseNumber: String(length = 32) @unique
    fullName: String(min = 3, max = 120)
    specialty: String(min = 2, max = 80)
    consultationFee: Decimal(precision = 10, scale = 2) @positive
    bio: String? @text
    avatar: String? @image

    relations {
        account: UserAccount @relation(oneToOne)
        appointments: List<Appointment> @relation(oneToMany, mappedBy = "doctor")
    }

    endpoints {
        crud {
            paginate: true
            search: [fullName, specialty, licenseNumber]
            sort: [fullName, consultationFee]
        }
    }
}

model PatientProfile {
    id: Long @id @auto
    medicalRecordNumber: String(length = 24) @unique
    fullName: String(min = 3, max = 120)
    dateOfBirth: Date
    bloodType: BloodType = UNKNOWN
    emergencyContactPhone: String(length = 20)
    allergies: String? @text

    relations {
        account: UserAccount @relation(oneToOne)
        appointments: List<Appointment> @relation(oneToMany, mappedBy = "patient")
        vitals: List<VitalReading> @relation(oneToMany, mappedBy = "patient")
        prescriptions: List<Prescription> @relation(oneToMany, mappedBy = "patient")
    }

    endpoints {
        crud {
            paginate: true
            search: [fullName, medicalRecordNumber]
            filter: [bloodType]
            sort: [fullName]
        }
    }
}

model Appointment {
    id: Long @id @auto
    scheduledAt: DateTime
    status: AppointmentStatus = SCHEDULED
    reasonForVisit: String(min = 5, max = 255)
    clinicalNotes: String? @text
    labReportDocument: String? @file
    radiologyScan: String? @image
    createdAt: DateTime @autoNow
    updatedAt: DateTime @autoUpdate

    relations {
        doctor: DoctorProfile @relation(manyToOne)
        patient: PatientProfile @relation(manyToOne)
    }

    endpoints {
        crud {
            paginate: true
            filter: [status, scheduledAt]
            sort: [scheduledAt]
        }

        // Confirm appointment booking
        post "/{id}/confirm" -> Void {
            description: "Confirms a scheduled appointment"
            guard: status == AppointmentStatus.SCHEDULED, "Only scheduled appointments can be confirmed"
            action: {
                status = AppointmentStatus.CONFIRMED,
                notify patient.account {
                    title: "Appointment Confirmed",
                    message: "Your appointment with Dr. " + doctor.fullName + " is confirmed."
                }
            }
        }

        // Check-in and start consultation
        post "/{id}/start" -> Void {
            description: "Starts the consultation visit"
            guard: status == AppointmentStatus.CONFIRMED, "Appointment must be confirmed before starting"
            action: {
                status = AppointmentStatus.IN_PROGRESS
            }
        }

        // Conclude consultation
        post "/{id}/complete" -> Void {
            description: "Concludes consultation and dispatches clinical summary"
            guard: status == AppointmentStatus.IN_PROGRESS, "Consultation must be in progress to complete"
            action: {
                status = AppointmentStatus.COMPLETED,
                notify patient.account {
                    title: "Consultation Complete",
                    message: "Your consultation summary and prescriptions are ready for review."
                }
            }
        }

        // Patient or clinic cancellation
        post "/{id}/cancel" -> Void {
            description: "Cancels an upcoming consultation"
            guard: status == AppointmentStatus.SCHEDULED || status == AppointmentStatus.CONFIRMED, "Cannot cancel completed or ongoing consultations"
            action: {
                status = AppointmentStatus.CANCELLED
            }
        }
    }
}
```

---

### 4. Biometric Telemetry & Vitals Tracking (`vitals.zen`)

Allows connected medical devices, wearable monitors, and triage nurses to record real-time telemetry with automatic threshold classification:

```zenith
enum VitalSeverity {
    NORMAL,
    ATTENTION,
    CRITICAL
}

model VitalReading {
    id: Long @id @auto
    recordedAt: DateTime @autoNow
    heartRateBpm: Int @min(30) @max(260)
    systolicBp: Int @min(50) @max(260)
    diastolicBp: Int @min(30) @max(160)
    oxygenSaturationPct: Int @min(50) @max(100)
    bodyTemperatureCelsius: Decimal(precision = 4, scale = 1)
    bloodGlucoseMgDl: Int? @min(20) @max(600)
    severity: VitalSeverity = NORMAL
    deviceIdentifier: String(length = 64)?

    relations {
        patient: PatientProfile @relation(manyToOne)
    }

    endpoints {
        crud {
            paginate: true
            filter: [severity, recordedAt]
            sort: [recordedAt]
        }

        // Flag vital reading as critical and alert attending doctor
        post "/{id}/flag-critical" -> Void {
            description: "Escalates anomalous vital reading to attending medical staff"
            action: {
                severity = VitalSeverity.CRITICAL,
                notify patient.account {
                    title: "Urgent Vital Alert",
                    message: "An anomalous telemetry reading was recorded. Please consult your care team."
                }
            }
        }
    }
}
```

---

### 5. Prescriptions & Pharmacy Orders (`prescriptions.zen`)

Manages pharmacy medication orders, dosage instructions, and fulfillment status:

```zenith
enum PrescriptionStatus {
    ISSUED,
    TRANSMITTED_TO_PHARMACY,
    DISPENSED,
    DISCONTINUED
}

dto PrescriptionSummaryResponse {
    id: Long
    medicationName: String
    dosage: String
    instructions: String
    refillsRemaining: Int
    status: PrescriptionStatus
    prescribedByName: String
}

model Prescription {
    id: Long @id @auto
    medicationName: String(min = 2, max = 150)
    dosage: String(min = 2, max = 60)
    frequency: String(min = 2, max = 100)
    durationDays: Int @min(1)
    refillsRemaining: Int @min(0) = 0
    instructions: String @text
    status: PrescriptionStatus = ISSUED
    issuedAt: DateTime @autoNow
    validUntil: Date

    relations {
        patient: PatientProfile @relation(manyToOne)
        doctor: DoctorProfile @relation(manyToOne)
    }

    endpoints {
        crud {
            paginate: true
            filter: [status]
            search: [medicationName]
            sort: [issuedAt]
        }

        // Transmit prescription to fulfillment pharmacy
        post "/{id}/transmit" -> PrescriptionSummaryResponse {
            description: "Transmits prescription to partner pharmacy network"
            guard: status == PrescriptionStatus.ISSUED, "Only newly issued prescriptions can be transmitted"
            action: {
                status = PrescriptionStatus.TRANSMITTED_TO_PHARMACY
            }
        }
    }
}
```

---

### 6. Scheduled Clinical Workers (`jobs.zen`)

Leverages Zenith's `@EnableScheduling` transpiler to automate daily patient communications and clinical safety checks:

```zenith
job AppointmentReminderWorker {
    schedule: "0 0 8 * * ?"
    description: "Sends reminders for consultations scheduled in the next 24 hours"
    action: {
        Appointment.filter(status == AppointmentStatus.CONFIRMED && scheduledAt <= daysFromNow(1))
            .forEach(appt -> {
                notify appt.patient.account {
                    title: "Appointment Reminder",
                    message: "Reminder: You have an upcoming appointment with Dr. " + appt.doctor.fullName + " scheduled for tomorrow."
                }
            })
    }
}

job StaleAppointmentCleanupWorker {
    schedule: "0 0 2 * * ?"
    description: "Marks unconfirmed pending consultations as NO_SHOW if date has passed"
    action: {
        Appointment.filter(status == AppointmentStatus.SCHEDULED && scheduledAt < daysAgo(1))
            .forEach(appt -> {
                appt.status = AppointmentStatus.NO_SHOW
            })
    }
}
```

---

### 7. Relational Mock Seeding (`seeds.zen`)

Provides out-of-the-box demo records for instant local development and validation in Zenith Studio PRO:

```zenith
seed UserAccount[1] {
    email: "dr.sarah.connor@pulsehealth.org",
    password: "Password123!",
    role: MedicalRole.DOCTOR,
    status: AccountStatus.ACTIVE
}

seed UserAccount[2] {
    email: "patient.john.doe@gmail.com",
    password: "Password123!",
    role: MedicalRole.PATIENT,
    status: AccountStatus.ACTIVE
}

seed DoctorProfile[1] {
    licenseNumber: "MED-TX-99201",
    fullName: "Dr. Sarah Connor, MD",
    specialty: "Cardiology",
    consultationFee: 175.00,
    account: UserAccount[1]
}

seed PatientProfile[1] {
    medicalRecordNumber: "MRN-2026-00481",
    fullName: "John Doe",
    dateOfBirth: "1988-04-12",
    bloodType: BloodType.O_POS,
    emergencyContactPhone: "+1-555-019-2834",
    allergies: "Penicillin, NSAIDs",
    account: UserAccount[2]
}

seed VitalReading[1] {
    heartRateBpm: 72,
    systolicBp: 118,
    diastolicBp: 78,
    oxygenSaturationPct: 99,
    bodyTemperatureCelsius: 36.6,
    bloodGlucoseMgDl: 95,
    severity: VitalSeverity.NORMAL,
    deviceIdentifier: "PULSE-BAND-SN401",
    patient: PatientProfile[1]
}
```

---

### 8. Root Entrypoint (`schema.zen`)

Aggregates all modules into a single compilation unit:

```zenith
import "./config.zen"
import "./auth.zen"
import "./clinical.zen"
import "./vitals.zen"
import "./prescriptions.zen"
import "./jobs.zen"
import "./seeds.zen"
```

---

## 🛠 Compilation & Execution Instructions

### Visual Exploration with Zenith Studio PRO
Launch the local visual architecture IDE to inspect the schema, explore live SVG entity diagrams, and execute mock REST calls:

```cmd
bin\zenith.bat studio zenith-exemples\pulse-health\schema.zen --port 3000
```

### Headless Production Compilation
Generate the full Spring Boot 3 Maven application:

```cmd
bin\zenith.bat build zenith-exemples\pulse-health\schema.zen -o generated\pulse-health
```

### Hot-Reload Development Daemon
Watch for edits across all `.zen` files and rebuild in milliseconds:

```cmd
bin\zenith.bat watch zenith-exemples\pulse-health\schema.zen -o generated\pulse-health
```

---

## 🧪 Generated Test Suite Topology

When compiled via `zenith build`, Zenith automatically synthesizes isolated test slices under `src/test/`:

| Test Class | Slice / Framework | Scope of Verification |
|:---|:---|:---|
| `PulseHealthApplicationTests.java` | `@SpringBootTest` | Verifies full Spring context bootstrapping and bean wiring. |
| `AuthControllerTest.java` | `MockMvc` | Tests authentication routes, invalid passwords, and expired JWT handling. |
| `AppointmentControllerTest.java` | `MockMvc` + `@WithMockUser` | Asserts lifecycle transitions (`/confirm`, `/start`, `/complete`), guards, and role permissions. |
| `VitalReadingRepositoryTest.java` | `@DataJpaTest` | Verifies PostgreSQL/H2 table constraints, severity queries, and patient foreign keys. |
| `PrescriptionControllerTest.java` | `MockMvc` | Validates prescription transmission guards and DTO response mapping. |

---

## 📦 Container Deployment

The project emits a multi-stage `Dockerfile` and `docker-compose.yml` linking the API service with PostgreSQL 15 and MinIO:

```bash
cd generated/pulse-health
docker compose up --build
```

Access Points:
* **Interactive Swagger UI**: `http://localhost:8081/swagger-ui.html`
* **Healthcheck & Actuator**: `http://localhost:8081/actuator/health`
* **MinIO Object Storage Console**: `http://localhost:9001` (Credentials configured in `docker-compose.yml`)

