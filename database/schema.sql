-- =====================================================================================
--  SMART CAMPUS INTEGRATED MANAGEMENT SYSTEM
--  File        : database/schema.sql
--  Description : Creates the `smart_campus_db` database, all tables, indexes and
--                foreign-key relationships.
--  Engine      : MySQL 8.0+
--  Author      : Smart Campus PBL
--
--  HOW TO RUN (beginner friendly):
--      mysql -u root -p < database/schema.sql
--    or open MySQL Workbench -> open this file -> Execute (lightning bolt)
-- =====================================================================================

DROP DATABASE IF EXISTS smart_campus_db;
CREATE DATABASE smart_campus_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_campus_db;

SET FOREIGN_KEY_CHECKS = 1;

-- -------------------------------------------------------------------------------------
-- 1. users  (accounts used for login / authentication)
--    password is stored as a BCrypt HASH - never as plain text.
-- -------------------------------------------------------------------------------------
CREATE TABLE users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(80)  NOT NULL UNIQUE,
    email         VARCHAR(120) NOT NULL UNIQUE,
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt hash',
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'STUDENT'
                  CHECK (role IN ('ADMIN','FACULTY','STUDENT')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role (role)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 2. user_sessions  (opaque bearer tokens issued at login - file-free token store)
-- -------------------------------------------------------------------------------------
CREATE TABLE user_sessions (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT          NOT NULL,
    token      VARCHAR(64)  NOT NULL UNIQUE,
    expires_at DATETIME     NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 3. students
-- -------------------------------------------------------------------------------------
CREATE TABLE students (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    usn            VARCHAR(20)  NOT NULL UNIQUE COMMENT 'University Seat Number',
    name           VARCHAR(120) NOT NULL,
    email          VARCHAR(120) NOT NULL UNIQUE,
    phone          VARCHAR(20)  NOT NULL,
    gender         VARCHAR(10)  NOT NULL CHECK (gender IN ('MALE','FEMALE','OTHER')),
    date_of_birth  DATE         NOT NULL,
    department     VARCHAR(60)  NOT NULL,
    semester       INT          NOT NULL CHECK (semester BETWEEN 1 AND 8),
    section        VARCHAR(5)   NOT NULL DEFAULT 'A',
    address        VARCHAR(255) NULL,
    admission_year INT          NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE','INACTIVE','COMPLETED')),
    user_id        INT          NULL,
    class_teacher_id INT        NULL COMMENT 'Faculty member assigned as class teacher',
    mentor_id      INT          NULL COMMENT 'Faculty member assigned as mentor',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_students_department (department),
    INDEX idx_students_semester   (semester),
    INDEX idx_students_status     (status),
    INDEX idx_students_class_teacher (class_teacher_id),
    INDEX idx_students_mentor        (mentor_id)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 4. faculty
-- -------------------------------------------------------------------------------------
CREATE TABLE faculty (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    employee_id    VARCHAR(20)  NOT NULL UNIQUE,
    name           VARCHAR(120) NOT NULL,
    email          VARCHAR(120) NOT NULL UNIQUE,
    phone          VARCHAR(20)  NOT NULL,
    department     VARCHAR(60)  NOT NULL,
    designation    VARCHAR(60)  NOT NULL,
    qualification  VARCHAR(120) NULL,
    joining_date   DATE         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE','ON_LEAVE','INACTIVE')),
    user_id        INT          NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_faculty_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_faculty_department (department)
) ENGINE=InnoDB;

-- students was created before faculty, so its mentor/class-teacher foreign keys
-- are added here once the faculty table exists.
ALTER TABLE students
    ADD CONSTRAINT fk_student_class_teacher FOREIGN KEY (class_teacher_id)
        REFERENCES faculty (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_student_mentor FOREIGN KEY (mentor_id)
        REFERENCES faculty (id) ON DELETE SET NULL;

-- -------------------------------------------------------------------------------------
-- 5. courses   (faculty_id -> faculty.id  : real many-to-one relationship)
-- -------------------------------------------------------------------------------------
CREATE TABLE courses (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    course_code  VARCHAR(20)  NOT NULL UNIQUE,
    course_name  VARCHAR(150) NOT NULL,
    department   VARCHAR(60)  NOT NULL,
    semester     INT          NOT NULL CHECK (semester BETWEEN 1 AND 8),
    credits      INT          NOT NULL CHECK (credits BETWEEN 1 AND 10),
    faculty_id   INT          NULL,
    description  VARCHAR(500) NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                 CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_course_faculty FOREIGN KEY (faculty_id)
        REFERENCES faculty (id) ON DELETE SET NULL,
    INDEX idx_courses_department (department),
    INDEX idx_courses_semester   (semester)
) ENGINE=InnoDB;

-- =====================================================================================
--  ACADEMIC ENHANCEMENT TABLES
--  Enrollment is derived from the existing students.department + students.semester
--  relationship to courses.department + courses.semester.  No duplicate enrollment
--  table is introduced; this keeps existing student/course data compatible.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 5a. academic_settings  (single configurable eligibility rule row, id = 1)
-- -------------------------------------------------------------------------------------
CREATE TABLE academic_settings (
    id                         INT PRIMARY KEY,
    min_attendance_percentage  DECIMAL(5,2) NOT NULL DEFAULT 75.00,
    min_marks_percentage       DECIMAL(5,2) NOT NULL DEFAULT 40.00,
    min_assessments_required   INT          NOT NULL DEFAULT 1,
    min_attendance_records     INT          NOT NULL DEFAULT 1,
    require_marks              BOOLEAN      NOT NULL DEFAULT TRUE,
    require_attendance         BOOLEAN      NOT NULL DEFAULT TRUE,
    late_counts_as_present     BOOLEAN      NOT NULL DEFAULT TRUE,
    rules_configured           BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_by_user_id         INT          NULL,
    updated_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_settings_user FOREIGN KEY (updated_by_user_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CHECK (min_attendance_percentage BETWEEN 0 AND 100),
    CHECK (min_marks_percentage BETWEEN 0 AND 100),
    CHECK (min_assessments_required >= 0),
    CHECK (min_attendance_records >= 0)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 5b. assessments  (one assessment belongs to one course/subject)
-- -------------------------------------------------------------------------------------
CREATE TABLE assessments (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    course_id             INT          NOT NULL,
    title                 VARCHAR(150) NOT NULL,
    assessment_type       VARCHAR(30)  NOT NULL DEFAULT 'OTHER'
                          CHECK (assessment_type IN ('QUIZ','ASSIGNMENT','MIDTERM',
                                                     'PRACTICAL','PROJECT','FINAL','OTHER')),
    term                  VARCHAR(30)  NOT NULL,
    max_score             DECIMAL(6,2) NOT NULL CHECK (max_score > 0),
    weight                DECIMAL(5,2) NULL
                          CHECK (weight IS NULL OR (weight >= 0 AND weight <= 100)),
    assessment_date       DATE         NULL,
    published             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by_faculty_id INT          NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_assessment_course FOREIGN KEY (course_id)
        REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_faculty FOREIGN KEY (created_by_faculty_id)
        REFERENCES faculty (id) ON DELETE SET NULL,
    UNIQUE KEY uq_assessment_course_title_term (course_id, title, term),
    INDEX idx_assessments_course (course_id, term),
    INDEX idx_assessments_published (published)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 5c. student_marks  (nullable score = not entered yet)
-- -------------------------------------------------------------------------------------
CREATE TABLE student_marks (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    assessment_id         INT          NOT NULL,
    student_id            INT          NOT NULL,
    score                 DECIMAL(6,2) NULL
                          CHECK (score IS NULL OR score >= 0),
    remarks               VARCHAR(255) NULL,
    entered_by_faculty_id INT          NULL,
    entered_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_mark_assessment FOREIGN KEY (assessment_id)
        REFERENCES assessments (id) ON DELETE CASCADE,
    CONSTRAINT fk_mark_student FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_mark_faculty FOREIGN KEY (entered_by_faculty_id)
        REFERENCES faculty (id) ON DELETE SET NULL,
    UNIQUE KEY uq_mark_assessment_student (assessment_id, student_id),
    INDEX idx_marks_student (student_id)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 5d. attendance  (one row per student + course + date; corrections update it)
-- -------------------------------------------------------------------------------------
CREATE TABLE attendance (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    student_id            INT         NOT NULL,
    course_id             INT         NOT NULL,
    attendance_date       DATE        NOT NULL,
    status                VARCHAR(10) NOT NULL
                          CHECK (status IN ('PRESENT','ABSENT','LATE')),
    remarks               VARCHAR(255) NULL,
    marked_by_faculty_id  INT         NULL,
    marked_at             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_faculty_id INT         NULL,
    updated_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_course FOREIGN KEY (course_id)
        REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_marked_by FOREIGN KEY (marked_by_faculty_id)
        REFERENCES faculty (id) ON DELETE SET NULL,
    CONSTRAINT fk_attendance_updated_by FOREIGN KEY (updated_by_faculty_id)
        REFERENCES faculty (id) ON DELETE SET NULL,
    UNIQUE KEY uq_attendance_student_course_date (student_id, course_id, attendance_date),
    INDEX idx_attendance_course_date (course_id, attendance_date),
    INDEX idx_attendance_student (student_id)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 5e. assignments  (published assignments are visible to enrolled students)
-- -------------------------------------------------------------------------------------
CREATE TABLE assignments (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    course_id             INT           NOT NULL,
    title                 VARCHAR(150)  NOT NULL,
    instructions          TEXT          NOT NULL,
    due_at                DATETIME      NOT NULL,
    allowed_file_types    VARCHAR(120)  NOT NULL DEFAULT 'pdf,doc,docx',
    max_file_size_mb      INT           NOT NULL DEFAULT 10
                          CHECK (max_file_size_mb BETWEEN 1 AND 50),
    max_score             DECIMAL(6,2)  NOT NULL DEFAULT 100 CHECK (max_score > 0),
    allow_late            BOOLEAN       NOT NULL DEFAULT TRUE,
    allow_resubmission    BOOLEAN       NOT NULL DEFAULT FALSE,
    published             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by_faculty_id INT           NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_assignment_course FOREIGN KEY (course_id)
        REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_faculty FOREIGN KEY (created_by_faculty_id)
        REFERENCES faculty (id) ON DELETE SET NULL,
    UNIQUE KEY uq_assignment_course_title (course_id, title),
    INDEX idx_assignments_course_due (course_id, due_at),
    INDEX idx_assignments_published (published)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 5f. assignment_submissions  (secure metadata; the file itself lives outside web root)
-- -------------------------------------------------------------------------------------
CREATE TABLE assignment_submissions (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    assignment_id         INT           NOT NULL,
    student_id            INT           NOT NULL,
    original_file_name    VARCHAR(255)  NOT NULL,
    stored_file_name      VARCHAR(120)  NOT NULL,
    content_type          VARCHAR(120)  NULL,
    file_size             BIGINT        NOT NULL CHECK (file_size > 0),
    submitted_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    late                  BOOLEAN       NOT NULL DEFAULT FALSE,
    attempt_no            INT           NOT NULL DEFAULT 1 CHECK (attempt_no > 0),
    status                VARCHAR(20)   NOT NULL DEFAULT 'SUBMITTED'
                          CHECK (status IN ('SUBMITTED','LATE','GRADED')),
    grade                 DECIMAL(6,2)  NULL
                          CHECK (grade IS NULL OR grade >= 0),
    feedback              VARCHAR(1000) NULL,
    reviewed_by_faculty_id INT          NULL,
    reviewed_at           DATETIME      NULL,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_submission_assignment FOREIGN KEY (assignment_id)
        REFERENCES assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_submission_student FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_submission_faculty FOREIGN KEY (reviewed_by_faculty_id)
        REFERENCES faculty (id) ON DELETE SET NULL,
    UNIQUE KEY uq_submission_assignment_student (assignment_id, student_id),
    UNIQUE KEY uq_submission_stored_file (stored_file_name),
    INDEX idx_submissions_student (student_id),
    INDEX idx_submissions_status (status)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 6. classrooms
-- -------------------------------------------------------------------------------------
CREATE TABLE classrooms (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    room_number  VARCHAR(20)  NOT NULL UNIQUE,
    building     VARCHAR(60)  NOT NULL,
    floor        INT          NOT NULL DEFAULT 1,
    capacity     INT          NOT NULL CHECK (capacity > 0),
    type         VARCHAR(30)  NOT NULL DEFAULT 'CLASSROOM'
                 CHECK (type IN ('CLASSROOM','SEMINAR_HALL','AUDITORIUM','CONFERENCE_ROOM')),
    facilities   VARCHAR(255) NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                 CHECK (status IN ('ACTIVE','MAINTENANCE','INACTIVE')),
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_classrooms_building (building)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 7. laboratories
-- -------------------------------------------------------------------------------------
CREATE TABLE laboratories (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    lab_number      VARCHAR(20)  NOT NULL UNIQUE,
    lab_name        VARCHAR(120) NOT NULL,
    building        VARCHAR(60)  NOT NULL,
    department      VARCHAR(60)  NOT NULL,
    capacity        INT          NOT NULL CHECK (capacity > 0),
    lab_type        VARCHAR(40)  NOT NULL,
    equipment_count INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','MAINTENANCE','INACTIVE')),
    description     VARCHAR(500) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_labs_department (department)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 8. equipment
-- -------------------------------------------------------------------------------------
CREATE TABLE equipment (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    equipment_name VARCHAR(120) NOT NULL,
    category       VARCHAR(60)  NOT NULL,
    serial_number  VARCHAR(60)  NOT NULL UNIQUE,
    quantity       INT          NOT NULL DEFAULT 1 CHECK (quantity >= 0),
    location       VARCHAR(120) NOT NULL,
    purchase_date  DATE         NULL,
    -- `condition` is a MySQL reserved word, therefore it must be back-quoted
    `condition`    VARCHAR(30)  NOT NULL DEFAULT 'NEW'
                   CHECK (`condition` IN ('NEW','GOOD','MAINTENANCE_REQUIRED','DAMAGED')),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE','MAINTENANCE','INACTIVE')),
    description    VARCHAR(500) NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_equipment_category (category)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 9. activities  (campus events)
-- -------------------------------------------------------------------------------------
CREATE TABLE activities (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    activity_name     VARCHAR(150) NOT NULL,
    description       VARCHAR(1000) NULL,
    category          VARCHAR(30)  NOT NULL DEFAULT 'OTHER'
                      CHECK (category IN ('TECHNICAL','CULTURAL','SPORTS','WORKSHOP',
                                          'SEMINAR','CLUB_ACTIVITY','OTHER')),
    organizer         VARCHAR(120) NOT NULL,
    venue             VARCHAR(120) NOT NULL,
    start_date        DATE         NOT NULL,
    end_date          DATE         NOT NULL,
    start_time        TIME         NOT NULL,
    end_time          TIME         NOT NULL,
    max_participants  INT          NOT NULL DEFAULT 100 CHECK (max_participants > 0),
    status            VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED'
                      CHECK (status IN ('SCHEDULED','ONGOING','COMPLETED','CANCELLED')),
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_activities_dates (start_date, end_date)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 10. bookings  (resource booking - the concurrency-critical table)
--
--  resource_type + resource_id form a POLYMORPHIC association:
--      'CLASSROOM'  -> classrooms.id
--      'LABORATORY' -> laboratories.id
--      'EQUIPMENT'  -> equipment.id
--  A single foreign key is impossible here (three candidate tables), so the service
--  layer validates that the resource exists and is active before inserting.
--  requested_by DOES have a real foreign key to users.id.
-- -------------------------------------------------------------------------------------
CREATE TABLE bookings (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    booking_ref      VARCHAR(20)  NOT NULL UNIQUE,
    resource_type    VARCHAR(20)  NOT NULL
                     CHECK (resource_type IN ('CLASSROOM','LABORATORY','EQUIPMENT')),
    resource_id      INT          NOT NULL,
    resource_name    VARCHAR(150) NOT NULL COMMENT 'denormalised snapshot for display',
    requested_by     INT          NOT NULL,
    requested_by_name VARCHAR(120) NOT NULL,
    booking_date     DATE         NOT NULL,
    start_time       TIME         NOT NULL,
    end_time         TIME         NOT NULL,
    purpose          VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED')),
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking_user FOREIGN KEY (requested_by)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_booking_time CHECK (end_time > start_time),
    -- composite index makes the overlap-detection query very fast
    INDEX idx_booking_lookup (resource_type, resource_id, booking_date, status)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- 11. activity_logs  (audit trail - demonstrates file/stream friendly data)
-- -------------------------------------------------------------------------------------
CREATE TABLE activity_logs (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT          NULL,
    user_name  VARCHAR(120) NULL,
    action     VARCHAR(60)  NOT NULL,
    module     VARCHAR(40)  NOT NULL,
    details    VARCHAR(500) NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_logs_module (module)
) ENGINE=InnoDB;
