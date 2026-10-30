-- =====================================================================================
--  SMART CAMPUS - ACADEMIC ENHANCEMENT MIGRATION
--
--  Safe to run against an existing smart_campus_db that already contains the
--  original 11 tables and real student/faculty/course data.  It only adds:
--    * students.class_teacher_id / students.mentor_id
--    * academic_settings, assessments, student_marks, attendance,
--      assignments, assignment_submissions
--
--  The script is idempotent: columns, indexes and foreign keys are checked in
--  information_schema before they are added, and every table uses IF NOT EXISTS.
--
--  Run:
--      mysql -u root -p smart_campus_db < database/academic_migration.sql
--  or execute this file in MySQL Workbench against smart_campus_db.
-- =====================================================================================

SET NAMES utf8mb4;

-- -------------------------------------------------------------------------------------
-- 1. Add the class teacher / mentor columns to the existing students table.
-- -------------------------------------------------------------------------------------
SET @has_class_teacher := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'students'
      AND COLUMN_NAME = 'class_teacher_id'
);

SET @sql := IF(@has_class_teacher = 0,
    'ALTER TABLE students
        ADD COLUMN class_teacher_id INT NULL COMMENT ''Faculty member assigned as class teacher'',
        ADD COLUMN mentor_id INT NULL COMMENT ''Faculty member assigned as mentor'',
        ADD INDEX idx_students_class_teacher (class_teacher_id),
        ADD INDEX idx_students_mentor (mentor_id)',
    'SELECT 1');
PREPARE academic_stmt FROM @sql;
EXECUTE academic_stmt;
DEALLOCATE PREPARE academic_stmt;

-- -------------------------------------------------------------------------------------
-- 2. Foreign keys (added after the columns exist).
-- -------------------------------------------------------------------------------------
SET @has_ct_fk := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'students'
      AND CONSTRAINT_NAME = 'fk_student_class_teacher'
);
SET @sql := IF(@has_ct_fk = 0,
    'ALTER TABLE students ADD CONSTRAINT fk_student_class_teacher
        FOREIGN KEY (class_teacher_id) REFERENCES faculty (id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE academic_stmt FROM @sql;
EXECUTE academic_stmt;
DEALLOCATE PREPARE academic_stmt;

SET @has_mentor_fk := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'students'
      AND CONSTRAINT_NAME = 'fk_student_mentor'
);
SET @sql := IF(@has_mentor_fk = 0,
    'ALTER TABLE students ADD CONSTRAINT fk_student_mentor
        FOREIGN KEY (mentor_id) REFERENCES faculty (id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE academic_stmt FROM @sql;
EXECUTE academic_stmt;
DEALLOCATE PREPARE academic_stmt;

-- -------------------------------------------------------------------------------------
-- 3. Configurable eligibility rules (single row, id = 1).
--    rules_configured = FALSE means the UI shows Pending / Insufficient data until
--    an administrator explicitly saves the thresholds.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS academic_settings (
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
-- 4. Assessments and marks.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assessments (
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

CREATE TABLE IF NOT EXISTS student_marks (
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
-- 5. Attendance register.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance (
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
-- 6. Assignments and secure submission metadata.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assignments (
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

CREATE TABLE IF NOT EXISTS assignment_submissions (
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
-- 7. Initial rule row (unconfigured).  The demo seed file configures it explicitly;
--    production administrators should save the thresholds in Academic Setup.
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO academic_settings (id) VALUES (1);
