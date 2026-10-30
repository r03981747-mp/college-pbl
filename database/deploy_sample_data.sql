-- =====================================================================================
--  SMART CAMPUS - DEPLOYMENT SAMPLE DATA
--
--  Run after database/deploy_schema.sql, against the same database:
--      mysql --ssl-mode=REQUIRED -h HOST -P PORT -u USER -p DATABASE < database/deploy_sample_data.sql
-- =====================================================================================

SET NAMES utf8mb4;
-- =====================================================================================
--  SMART CAMPUS INTEGRATED MANAGEMENT SYSTEM
--  File        : database/sample_data.sql
--  Description : Realistic test data for every table.  Run AFTER schema.sql
--
--  HOW TO RUN:
--      mysql -u root -p smart_campus_db < database/sample_data.sql
--    or open MySQL Workbench, select smart_campus_db, then execute this file.
--
--  DEMO LOGIN ACCOUNTS (passwords are BCrypt hashes, never plain text)
--      admin@smartcampus.com    / Admin@123     (ADMIN)
--      faculty@smartcampus.com  / Faculty@123   (FACULTY)
--      student@smartcampus.com  / Student@123   (STUDENT)
-- =====================================================================================
-- Preserve referential integrity while loading independent tables.
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE assignment_submissions;
TRUNCATE TABLE assignments;
TRUNCATE TABLE attendance;
TRUNCATE TABLE student_marks;
TRUNCATE TABLE assessments;
TRUNCATE TABLE academic_settings;
TRUNCATE TABLE activity_logs;
TRUNCATE TABLE bookings;
TRUNCATE TABLE activities;
TRUNCATE TABLE equipment;
TRUNCATE TABLE laboratories;
TRUNCATE TABLE classrooms;
TRUNCATE TABLE courses;
TRUNCATE TABLE students;
TRUNCATE TABLE faculty;
TRUNCATE TABLE user_sessions;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

-- -------------------------------------------------------------------------------------
-- USERS  (bcrypt hashes generated with jBCrypt, cost 10)
-- -------------------------------------------------------------------------------------
INSERT INTO users (id, username, email, password, full_name, role, status) VALUES
(1,'admin','admin@smartcampus.com','$2a$10$XcH0DApeuM90nsCMRGYkSucHFctmtyIvqa.QbXlwTjaJNsAOTsWBe','System Administrator','ADMIN','ACTIVE'),
(2,'faculty','faculty@smartcampus.com','$2a$10$Z.jFcH3sYroufJImUzs5/OaASCjDYoWR4Ay5ZHvkC.R6QoL5aSIL2','Dr. Priya Sharma','FACULTY','ACTIVE'),
(3,'student','student@smartcampus.com','$2a$10$IK5fYB5a2EwIsDHtqTZ1YebTaCz1iLjVg9XheHwrN.Tg8T4XepS.i','Rahul Kumar','STUDENT','ACTIVE'),
(4,'anitha.rao','anitha.rao@smartcampus.com','$2a$10$Z.jFcH3sYroufJImUzs5/OaASCjDYoWR4Ay5ZHvkC.R6QoL5aSIL2','Prof. Anitha Rao','FACULTY','ACTIVE'),
(5,'deepika.n','deepika.n@smartcampus.com','$2a$10$IK5fYB5a2EwIsDHtqTZ1YebTaCz1iLjVg9XheHwrN.Tg8T4XepS.i','Deepika Nair','STUDENT','ACTIVE'),
(6,'arjun.s','arjun.s@smartcampus.com','$2a$10$IK5fYB5a2EwIsDHtqTZ1YebTaCz1iLjVg9XheHwrN.Tg8T4XepS.i','Arjun Singh','STUDENT','ACTIVE'),
(7,'lab.assistant','lab.assistant@smartcampus.com','$2a$10$Z.jFcH3sYroufJImUzs5/OaASCjDYoWR4Ay5ZHvkC.R6QoL5aSIL2','Sandeep Shetty','FACULTY','INACTIVE');

-- -------------------------------------------------------------------------------------
-- FACULTY
-- -------------------------------------------------------------------------------------
INSERT INTO faculty (id, employee_id, name, email, phone, department, designation, qualification, joining_date, status, user_id) VALUES
(1,'EMP-CS-001','Dr. Priya Sharma','faculty@smartcampus.com','9845012345','Computer Science','Professor','Ph.D. in Computer Science','2015-06-15','ACTIVE',2),
(2,'EMP-CS-002','Prof. Anitha Rao','anitha.rao@smartcampus.com','9845012346','Computer Science','Associate Professor','M.Tech, Ph.D. pursuing','2018-07-01','ACTIVE',4),
(3,'EMP-CS-003','Dr. Vikram Iyer','vikram.iyer@smartcampus.com','9845012347','Computer Science','Assistant Professor','Ph.D. in Data Analytics','2020-08-10','ACTIVE',NULL),
(4,'EMP-EC-001','Dr. Meena Krishnan','meena.k@smartcampus.com','9845012348','Electronics & Communication','Professor','Ph.D. in VLSI Design','2013-01-20','ACTIVE',NULL),
(5,'EMP-ME-001','Prof. Suresh Patil','suresh.p@smartcampus.com','9845012349','Mechanical Engineering','Associate Professor','M.Tech in Thermal Engineering','2017-03-05','ON_LEAVE',NULL),
(6,'EMP-IT-001','Dr. Kavya Hegde','kavya.h@smartcampus.com','9845012350','Information Technology','Assistant Professor','Ph.D. in Cloud Computing','2021-09-01','ACTIVE',NULL);

-- -------------------------------------------------------------------------------------
-- STUDENTS
-- -------------------------------------------------------------------------------------
INSERT INTO students (id, usn, name, email, phone, gender, date_of_birth, department, semester, section, address, admission_year, status, user_id, class_teacher_id, mentor_id) VALUES
(1,'1CS21CS001','Rahul Kumar','student@smartcampus.com','9008001001','MALE','2003-05-14','Computer Science',5,'A','42, MG Road, Bengaluru',2021,'ACTIVE',3,1,2),
(2,'1CS21CS002','Deepika Nair','deepika.n@smartcampus.com','9008001002','FEMALE','2003-09-02','Computer Science',5,'A','12, Jayanagar, Bengaluru',2021,'ACTIVE',5,1,2),
(3,'1CS21CS003','Arjun Singh','arjun.s@smartcampus.com','9008001003','MALE','2002-12-21','Computer Science',5,'B','7, Indiranagar, Bengaluru',2021,'ACTIVE',6,2,1),
(4,'1CS22CS014','Fatima Begum','fatima.b@smartcampus.com','9008001004','FEMALE','2004-03-11','Computer Science',3,'A','88, Shivajinagar, Pune',2022,'ACTIVE',NULL,2,1),
(5,'1CS23CS021','Karthik Reddy','karthik.r@smartcampus.com','9008001005','MALE','2005-07-30','Computer Science',1,'A','5, Whitefield, Bengaluru',2023,'ACTIVE',NULL,1,2),
(6,'1EC21EC007','Sneha Patil','sneha.p@smartcampus.com','9008001006','FEMALE','2003-01-19','Electronics & Communication',5,'A','31, Malleswaram, Bengaluru',2021,'ACTIVE',NULL,4,NULL),
(7,'1EC22EC012','Rohit Verma','rohit.v@smartcampus.com','9008001007','MALE','2004-06-08','Electronics & Communication',3,'B','19, Rajajinagar, Bengaluru',2022,'ACTIVE',NULL,4,NULL),
(8,'1ME21ME004','Ananya Desai','ananya.d@smartcampus.com','9008001008','FEMALE','2003-11-05','Mechanical Engineering',5,'A','64, Basavanagudi, Bengaluru',2021,'ACTIVE',NULL,5,NULL),
(9,'1ME23ME019','Imran Khan','imran.k@smartcampus.com','9008001009','MALE','2005-02-17','Mechanical Engineering',1,'B','9, Koramangala, Bengaluru',2023,'ACTIVE',NULL,5,NULL),
(10,'1IT21IT005','Pooja Gowda','pooja.g@smartcampus.com','9008001010','FEMALE','2003-08-25','Information Technology',5,'A','27, Hebbal, Bengaluru',2021,'ACTIVE',NULL,6,NULL),
(11,'1IT22IT011','Naveen Kumar','naveen.k@smartcampus.com','9008001011','MALE','2004-04-13','Information Technology',3,'A','3, Jayanagar, Bengaluru',2022,'INACTIVE',NULL,6,NULL),
(12,'1CS20CS002','Shreya Rao','shreya.r@smartcampus.com','9008001012','FEMALE','2002-10-09','Computer Science',8,'A','55, Adugodi, Bengaluru',2020,'COMPLETED',NULL,NULL,NULL);

-- -------------------------------------------------------------------------------------
-- COURSES   (faculty_id -> faculty.id)
-- -------------------------------------------------------------------------------------
INSERT INTO courses (id, course_code, course_name, department, semester, credits, faculty_id, description, status) VALUES
(1,'CS501','Data Structures and Algorithms','Computer Science',5,4,1,'Arrays, linked lists, trees, graphs, sorting and algorithm analysis.', 'ACTIVE'),
(2,'CS502','Operating Systems','Computer Science',5,4,2,'Processes, threads, memory management, file systems and deadlock.', 'ACTIVE'),
(3,'CS503','Database Management Systems','Computer Science',5,4,1,'ER model, normalisation, SQL, transactions and concurrency control.', 'ACTIVE'),
(4,'CS504','Computer Networks','Computer Science',5,3,3,'OSI and TCP/IP models, routing, transport layer and network security.', 'ACTIVE'),
(5,'CS301','Object Oriented Programming','Computer Science',3,4,3,'Classes, inheritance, polymorphism, interfaces and exception handling in Java.', 'ACTIVE'),
(6,'CS701','Machine Learning','Computer Science',7,4,2,'Supervised and unsupervised learning, model evaluation and deployment.', 'ACTIVE'),
(7,'EC501','Digital Electronics','Electronics & Communication',5,3,4,'Combinational and sequential circuits, flip-flops and timers.', 'ACTIVE'),
(8,'ME501','Applied Thermodynamics','Mechanical Engineering',5,4,5,'Laws of thermodynamics, cycles, entropy and power plants.', 'ACTIVE'),
(9,'IT501','Web Technologies','Information Technology',5,3,6,'HTML5, CSS3, JavaScript, REST APIs and responsive design.', 'ACTIVE'),
(10,'IT301','Python Programming','Information Technology',3,4,6,'Core Python, functions, OOP, modules and file handling.', 'ACTIVE');

-- -------------------------------------------------------------------------------------
-- ACADEMIC SETTINGS (demo thresholds - administrators can change these in Academic Setup)
-- -------------------------------------------------------------------------------------
INSERT INTO academic_settings
    (id, min_attendance_percentage, min_marks_percentage, min_assessments_required,
     min_attendance_records, require_marks, require_attendance, late_counts_as_present,
     rules_configured, updated_by_user_id)
VALUES
    (1, 75.00, 40.00, 1, 1, TRUE, TRUE, TRUE, TRUE, 1);

-- -------------------------------------------------------------------------------------
-- ASSESSMENTS (published rows are visible to students; draft row demonstrates the workflow)
-- -------------------------------------------------------------------------------------
INSERT INTO assessments
    (id, course_id, title, assessment_type, term, max_score, weight, assessment_date,
     published, created_by_faculty_id)
VALUES
    (1, 1, 'Quiz 1', 'QUIZ', '2026-ODD', 10.00, 10.00, '2026-09-12', TRUE, 1),
    (2, 1, 'Midterm Examination', 'MIDTERM', '2026-ODD', 50.00, 40.00, '2026-10-05', TRUE, 1),
    (3, 1, 'Assignment 1', 'ASSIGNMENT', '2026-ODD', 20.00, 20.00, '2026-10-20', TRUE, 1),
    (4, 1, 'Internal Test 2', 'OTHER', '2026-ODD', 25.00, 15.00, '2026-11-12', FALSE, 1),
    (5, 2, 'Quiz 1', 'QUIZ', '2026-ODD', 10.00, 10.00, '2026-09-15', TRUE, 2),
    (6, 2, 'Midterm Examination', 'MIDTERM', '2026-ODD', 50.00, 40.00, '2026-10-07', TRUE, 2),
    (7, 3, 'Quiz 1', 'QUIZ', '2026-ODD', 10.00, 10.00, '2026-09-18', TRUE, 1),
    (8, 3, 'Midterm Examination', 'MIDTERM', '2026-ODD', 50.00, 40.00, '2026-10-09', TRUE, 1);

-- -------------------------------------------------------------------------------------
-- STUDENT MARKS (student 1 = strong, student 2 = borderline, student 3 = low)
-- -------------------------------------------------------------------------------------
INSERT INTO student_marks (assessment_id, student_id, score, remarks, entered_by_faculty_id) VALUES
    (1, 1, 8.00,  'Good understanding of complexity analysis.', 1),
    (2, 1, 42.00, 'Well structured answers.', 1),
    (3, 1, 18.00, 'Complete implementation and clear report.', 1),
    (5, 1, 9.00,  'Accurate scheduling answers.', 2),
    (6, 1, 45.00, 'Excellent attempt.', 2),
    (7, 1, 7.00,  'ER model is correct.', 1),
    (8, 1, 38.00, 'Normalisation answers need more detail.', 1),
    (1, 2, 6.00,  'Basic concepts are clear.', 1),
    (2, 2, 30.00, 'Some long answers incomplete.', 1),
    (3, 2, 15.00, 'Report is acceptable.', 1),
    (5, 2, 7.00,  'Good attempt.', 2),
    (6, 2, 33.00, 'Revise deadlock detection.', 2),
    (7, 2, 5.00,  'Needs more practice with keys.', 1),
    (8, 2, 28.00, 'Normalisation needs revision.', 1),
    (1, 3, 4.00,  'Revise asymptotic notation.', 1),
    (2, 3, 22.00, 'Several questions left blank.', 1),
    (3, 3, 12.00, 'Partial implementation.', 1),
    (5, 3, 5.00,  'Revise process synchronisation.', 2),
    (6, 3, 25.00, 'Needs improvement.', 2),
    (7, 3, 3.00,  'Revise relational algebra.', 1),
    (8, 3, 20.00, 'Normalisation answers incomplete.', 1);

-- -------------------------------------------------------------------------------------
-- ATTENDANCE (8 sessions x 3 subjects x 3 demo students)
--   student 1: 100% attended (one late counts as present)
--   student 2: 87.5% attended
--   student 3: 62.5% attended - demonstrates Not eligible
-- -------------------------------------------------------------------------------------
INSERT INTO attendance
    (student_id, course_id, attendance_date, status, remarks, marked_by_faculty_id)
SELECT s.id, c.id, d.dt,
       CASE
           WHEN s.id = 1 AND c.id = 2 AND d.dt = '2026-10-12' THEN 'ABSENT'
           WHEN s.id = 1 AND d.dt = '2026-10-07' THEN 'LATE'
           WHEN s.id = 2 AND c.id = 1 AND d.dt = '2026-10-14' THEN 'ABSENT'
           WHEN s.id = 2 AND d.dt = '2026-10-05' THEN 'LATE'
           WHEN s.id = 3 AND d.dt IN ('2026-10-05','2026-10-12','2026-10-19') THEN 'ABSENT'
           WHEN s.id = 3 AND d.dt = '2026-09-28' THEN 'LATE'
           ELSE 'PRESENT'
       END,
       NULL, c.faculty_id
FROM students s
JOIN courses c
  ON c.department = s.department
 AND c.semester = s.semester
 AND c.id IN (1, 2, 3)
CROSS JOIN (
    SELECT DATE('2026-09-21') AS dt UNION ALL
    SELECT DATE('2026-09-23') UNION ALL
    SELECT DATE('2026-09-28') UNION ALL
    SELECT DATE('2026-10-05') UNION ALL
    SELECT DATE('2026-10-07') UNION ALL
    SELECT DATE('2026-10-12') UNION ALL
    SELECT DATE('2026-10-14') UNION ALL
    SELECT DATE('2026-10-19')
) d
WHERE s.id IN (1, 2, 3);

-- -------------------------------------------------------------------------------------
-- ASSIGNMENTS (files are never stored in this table; submissions live in the upload area)
-- -------------------------------------------------------------------------------------
INSERT INTO assignments
    (id, course_id, title, instructions, due_at, allowed_file_types, max_file_size_mb,
     max_score, allow_late, allow_resubmission, published, created_by_faculty_id)
VALUES
    (1, 1, 'DSA Lab Report',
     'Submit a PDF or Word report for the graph traversal lab. Include your implementation approach, test cases and complexity analysis.',
     '2026-11-10 23:59:00', 'pdf,doc,docx', 10, 20.00, TRUE, TRUE, TRUE, 1),
    (2, 1, 'Algorithm Analysis Sheet',
     'Solve the five algorithm-analysis problems shared in class. Upload a single PDF or Word document.',
     '2026-10-20 17:00:00', 'pdf,doc,docx', 10, 10.00, FALSE, FALSE, TRUE, 1),
    (3, 2, 'OS Scheduling Case Study',
     'Compare FCFS, SJF and Round Robin for the supplied workload. Submit a PDF report with your calculations.',
     '2026-11-15 23:59:00', 'pdf,doc,docx', 10, 20.00, TRUE, FALSE, TRUE, 2),
    (4, 3, 'ER Diagram Design',
     'Design an ER diagram for the campus library and convert it into relational tables. Submit a PDF with the diagram and assumptions.',
     '2026-11-05 17:00:00', 'pdf,doc,docx,png,jpg', 10, 15.00, TRUE, TRUE, TRUE, 1),
    (5, 1, 'DSA Revision Quiz',
     'Draft assignment - publish it when the class is ready.',
     '2026-11-20 23:59:00', 'pdf', 5, 10.00, TRUE, FALSE, FALSE, 1);

-- -------------------------------------------------------------------------------------
-- CLASSROOMS
-- -------------------------------------------------------------------------------------
INSERT INTO classrooms (id, room_number, building, floor, capacity, type, facilities, status) VALUES
(1,'C-101','Main Building',1,60,'CLASSROOM','Projector, Whiteboard, Air Conditioner','ACTIVE'),
(2,'C-102','Main Building',1,60,'CLASSROOM','Projector, Whiteboard','ACTIVE'),
(3,'C-201','Main Building',2,45,'CLASSROOM','Smart Board, Whiteboard','ACTIVE'),
(4,'SH-01','Seminar Block',1,120,'SEMINAR_HALL','Projector, PA System, AC, Recording','ACTIVE'),
(5,'AUD-01','Auditorium Block',0,500,'AUDITORIUM','Stage, Lighting, PA System, AC','ACTIVE'),
(6,'CR-01','Admin Block',3,30,'CONFERENCE_ROOM','Video Conferencing, TV, Whiteboard','MAINTENANCE'),
(7,'C-305','Tech Block',3,50,'CLASSROOM','Projector, Whiteboard','INACTIVE');

-- -------------------------------------------------------------------------------------
-- LABORATORIES
-- -------------------------------------------------------------------------------------
INSERT INTO laboratories (id, lab_number, lab_name, building, department, capacity, lab_type, equipment_count, status, description) VALUES
(1,'LAB-01','Computer Programming Lab','Tech Block','Computer Science',40,'Programming Lab',40,'ACTIVE','Installed with Eclipse, IntelliJ and JDK 17 for Java/OOP practicals.'),
(2,'LAB-02','Database Systems Lab','Tech Block','Computer Science',36,'Database Lab',36,'ACTIVE','MySQL and Oracle instances with client workstations.'),
(3,'LAB-03','Electronics Lab','Science Block','Electronics & Communication',30,'Hardware Lab',28,'ACTIVE','Oscilloscopes, function generators and microcontroller kits.'),
(4,'LAB-04','Mechanical Workshop Lab','Workshop Block','Mechanical Engineering',25,'Workshop Lab',20,'MAINTENANCE','Lathe, milling and welding equipment under annual servicing.'),
(5,'LAB-05','Cloud Computing Lab','Tech Block','Information Technology',30,'Cloud Lab',30,'ACTIVE','Private cloud cluster with 30 virtualisation nodes.');

-- -------------------------------------------------------------------------------------
-- EQUIPMENT   (column `condition` is back-quoted because it is a SQL keyword)
-- -------------------------------------------------------------------------------------
INSERT INTO equipment (id, equipment_name, category, serial_number, quantity, location, purchase_date, `condition`, status, description) VALUES
(1,'Desktop Computers','Computing','SYS-1001',40,'LAB-01','2021-08-15','GOOD','ACTIVE','Core i7 workstations with 16 GB RAM.'),
(2,'HP Laser Printer','Printing','PRN-2001',3,'C-101','2022-01-10','NEW','ACTIVE','Network printer for department staff.'),
(3,'Digital Oscilloscope','Electronics','OSC-3001',12,'LAB-03','2020-11-05','GOOD','ACTIVE','200 MHz 4-channel digital storage oscilloscope.'),
(4,'Lathe Machine','Mechanical','MCH-4001',2,'LAB-04','2019-05-20','MAINTENANCE_REQUIRED','MAINTENANCE','Center lathe used for workshop training.'),
(5,'LCD Projector','Audio Visual','PRJ-5001',15,'SH-01','2022-07-12','GOOD','ACTIVE','4K projectors allocated to halls and classrooms.'),
(6,'Cisco Router','Networking','NET-6001',6,'LAB-02','2021-03-03','GOOD','ACTIVE','Used for computer networks practicals.'),
(7,'3D Printer','Fabrication','3DP-7001',2,'LAB-04','2023-02-18','NEW','ACTIVE','FDM printer for student prototype projects.'),
(8,'Server Rack','Computing','SRV-8001',1,'Server Room','2020-09-01','GOOD','ACTIVE','Hosts the campus ERP and database servers.'),
(9,'Microcontroller Kit','Electronics','MCK-9001',25,'LAB-03','2022-04-22','GOOD','ACTIVE','ARM and Arduino development kits.'),
(10,'Wi-Fi Access Point','Networking','WAP-10002',12,'Main Building','2023-06-30','NEW','ACTIVE','Campus-wide wireless coverage.'),
(11,'Chemical Fume Hood','Laboratory','FHM-11001',1,'Science Block','2018-12-11','DAMAGED','MAINTENANCE','Scratch glass and faulty exhaust - repair pending.'),
(12,'Sound System','Audio Visual','SND-12001',1,'AUD-01','2021-10-25','GOOD','ACTIVE','16 channel mixer with stage monitors.');

-- -------------------------------------------------------------------------------------
-- CAMPUS ACTIVITIES / EVENTS
-- -------------------------------------------------------------------------------------
INSERT INTO activities (id, activity_name, description, category, organizer, venue, start_date, end_date, start_time, end_time, max_participants, status) VALUES
(1,'AI in Healthcare Seminar','Guest lecture on diagnostic imaging and predictive healthcare models.','SEMINAR','Information Technology Department','SH-01','2026-10-09','2026-10-09','11:00:00','13:00:00',150,'SCHEDULED'),
(2,'Robotics Club Weekly Meet','Weekly build session covering line-follower and gripper assembly.','CLUB_ACTIVITY','Robotics Club','LAB-03','2026-10-06','2026-10-06','16:00:00','18:00:00',40,'SCHEDULED'),
(3,'Cloud and DevOps Workshop','Hands-on workshop on Docker, CI/CD pipelines and cloud deployment.','WORKSHOP','Computer Science Department','CR-01','2026-10-17','2026-10-17','10:00:00','16:00:00',60,'SCHEDULED'),
(4,'Inter-College Cricket Tournament','Knockout cricket tournament between affiliated colleges.','SPORTS','Sports Committee','Main Ground','2026-11-05','2026-11-07','08:00:00','17:00:00',200,'SCHEDULED'),
(5,'TechNova 2026','National level technical symposium with coding, robotics and paper presentation tracks.','TECHNICAL','Coding Club','SH-01','2026-11-12','2026-11-13','09:30:00','17:00:00',300,'SCHEDULED'),
(6,'Cultural Night - Rhythms','Evening of music, dance and drama performed by student clubs.','CULTURAL','Student Council','AUD-01','2026-10-24','2026-10-24','18:00:00','22:00:00',500,'SCHEDULED'),
(7,'Smart Campus Hackathon','48 hour hackathon to build solutions for campus problems.','TECHNICAL','Innovation Cell','C-201','2026-10-01','2026-10-03','09:00:00','18:00:00',120,'ONGOING'),
(8,'Annual Convocation 2025','Degree distribution ceremony for the graduating batch.','OTHER','Registrar Office','AUD-01','2025-05-28','2025-05-28','09:00:00','13:00:00',1000,'COMPLETED');

-- -------------------------------------------------------------------------------------
-- RESOURCE BOOKINGS
-- -------------------------------------------------------------------------------------
INSERT INTO bookings (id, booking_ref, resource_type, resource_id, resource_name, requested_by, requested_by_name, booking_date, start_time, end_time, purpose, status) VALUES
(1,'BK-0001','CLASSROOM',1,'C-101 - Main Building',2,'Dr. Priya Sharma','2026-10-06','09:00:00','10:30:00','DBMS lecture for Section A','APPROVED'),
(2,'BK-0002','LABORATORY',1,'LAB-01 - Computer Programming Lab',2,'Dr. Priya Sharma','2026-10-06','10:00:00','12:00:00','Java programming lab session','APPROVED'),
(3,'BK-0003','CLASSROOM',4,'SH-01 - Seminar Block',1,'System Administrator','2026-10-08','14:00:00','16:00:00','Faculty council meeting','PENDING'),
(4,'BK-0004','EQUIPMENT',5,'LCD Projector',6,'Arjun Singh','2026-10-09','11:00:00','13:00:00','Support for seminar presentation','PENDING'),
(5,'BK-0005','LABORATORY',2,'LAB-02 - Database Systems Lab',3,'Rahul Kumar','2026-10-07','15:00:00','17:00:00','Mini project testing','REJECTED'),
(6,'BK-0006','CLASSROOM',3,'C-201 - Main Building',4,'Prof. Anitha Rao','2026-10-10','09:00:00','11:00:00','OOP doubt clearing session','APPROVED'),
(7,'BK-0007','CLASSROOM',2,'C-102 - Main Building',2,'Dr. Priya Sharma','2026-09-28','10:00:00','11:30:00','Continuous assessment test','COMPLETED'),
(8,'BK-0008','EQUIPMENT',3,'Digital Oscilloscope',5,'Deepika Nair','2026-10-11','13:00:00','15:00:00','Circuit design prototype testing','PENDING'),
(9,'BK-0009','CLASSROOM',5,'AUD-01 - Auditorium Block',1,'System Administrator','2026-10-16','09:00:00','12:00:00','Annual day rehearsal','CANCELLED'),
(10,'BK-0010','LABORATORY',3,'LAB-03 - Electronics Lab',4,'Prof. Anitha Rao','2026-10-12','10:00:00','12:00:00','VLSI lab experiment','APPROVED');

-- -------------------------------------------------------------------------------------
-- ACTIVITY LOGS (audit trail, also exported as CSV from the Reports page)
-- -------------------------------------------------------------------------------------
INSERT INTO activity_logs (user_id, user_name, action, module, details) VALUES
(1,'System Administrator','LOGIN','AUTH','Administrator signed in from the web console.'),
(2,'Dr. Priya Sharma','CREATE','BOOKING','Created booking BK-0001 for classroom C-101.'),
(3,'Rahul Kumar','CREATE','BOOKING','Requested booking BK-0005 for laboratory LAB-02.'),
(1,'System Administrator','CREATE','STUDENT','Added student 1CS23CS021 (Karthik Reddy).'),
(4,'Prof. Anitha Rao','UPDATE','COURSE','Updated course CS301 credits.'),
(1,'System Administrator','EXPORT','REPORT','Exported students report to CSV.');
