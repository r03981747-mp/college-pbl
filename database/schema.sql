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
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_students_department (department),
    INDEX idx_students_semester   (semester),
    INDEX idx_students_status     (status)
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
