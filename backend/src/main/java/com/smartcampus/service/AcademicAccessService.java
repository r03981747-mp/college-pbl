package com.smartcampus.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.smartcampus.exception.CourseNotFoundException;
import com.smartcampus.exception.ForbiddenException;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.exception.StudentNotFoundException;
import com.smartcampus.model.Course;
import com.smartcampus.model.Faculty;
import com.smartcampus.model.Student;
import com.smartcampus.model.enums.Role;
import com.smartcampus.repository.CourseRepository;
import com.smartcampus.repository.FacultyRepository;
import com.smartcampus.repository.StudentRepository;
import com.smartcampus.security.AuthContext;
import com.smartcampus.security.AuthenticatedUser;

/**
 * One place for every academic authorization rule.
 *
 * <p>Enrollment is derived from the existing data model:
 * {@code students.department + students.semester} matches
 * {@code courses.department + courses.semester}.  A faculty member is
 * authorized for a course when {@code courses.faculty_id} points at them, and
 * for a student when they are the class teacher, the mentor, or teach an
 * active subject in that student's class.</p>
 */
@Service
public class AcademicAccessService {

    private final StudentRepository studentRepository;
    private final FacultyRepository facultyRepository;
    private final CourseRepository courseRepository;

    public AcademicAccessService(StudentRepository studentRepository,
                                 FacultyRepository facultyRepository,
                                 CourseRepository courseRepository) {
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.courseRepository = courseRepository;
    }

    public AuthenticatedUser currentUser() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new ForbiddenException("You must be signed in.");
        }
        return user;
    }

    public boolean isAdmin() {
        return currentUser().role() == Role.ADMIN;
    }

    public Student requireCurrentStudent() {
        AuthenticatedUser user = currentUser();
        if (user.role() != Role.STUDENT) {
            throw new ForbiddenException("This action is only available to students.");
        }
        return studentRepository.findByUserId(user.id())
                .orElseThrow(() -> new InvalidInputException(
                        "No student profile is linked to this account yet."));
    }

    public Faculty requireCurrentFaculty() {
        AuthenticatedUser user = currentUser();
        if (user.role() != Role.FACULTY) {
            throw new ForbiddenException("This action requires a faculty account.");
        }
        return facultyRepository.findByUserId(user.id())
                .orElseThrow(() -> new ForbiddenException(
                        "No faculty profile is linked to this account."));
    }

    public Faculty currentFacultyOrNull() {
        AuthenticatedUser user = currentUser();
        if (user.role() != Role.FACULTY) {
            return null;
        }
        return facultyRepository.findByUserId(user.id()).orElse(null);
    }

    public Long currentFacultyIdOrNull() {
        Faculty faculty = currentFacultyOrNull();
        return faculty == null ? null : faculty.getId();
    }

    public Student requireStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
    }

    public Course requireCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
    }

    /**
     * Admin or the faculty member assigned to the course.  Used for marks,
     * attendance and assignment management.
     */
    public Course requireManagedCourse(Long courseId) {
        Course course = requireCourse(courseId);
        AuthenticatedUser user = currentUser();
        if (user.role() == Role.ADMIN) {
            return course;
        }
        if (user.role() == Role.FACULTY) {
            Faculty faculty = requireCurrentFaculty();
            if (Objects.equals(course.getFacultyId(), faculty.getId())) {
                return course;
            }
            throw new ForbiddenException(
                    "You can only manage subjects assigned to you.");
        }
        throw new ForbiddenException("Students cannot manage academic records.");
    }

    /** Student enrolled in the course, or faculty teaching it / admin. */
    public Course requireViewableCourse(Long courseId) {
        Course course = requireCourse(courseId);
        AuthenticatedUser user = currentUser();
        if (user.role() == Role.ADMIN) {
            return course;
        }
        if (user.role() == Role.FACULTY) {
            Faculty faculty = requireCurrentFaculty();
            if (Objects.equals(course.getFacultyId(), faculty.getId())) {
                return course;
            }
            throw new ForbiddenException("You can only view subjects assigned to you.");
        }
        Student student = requireCurrentStudent();
        assertStudentEnrolled(course, student);
        return course;
    }

    public List<Course> visibleCourses() {
        AuthenticatedUser user = currentUser();
        if (user.role() == Role.ADMIN) {
            return courseRepository.findAllActive();
        }
        if (user.role() == Role.FACULTY) {
            return courseRepository.findActiveByFacultyId(requireCurrentFaculty().getId());
        }
        Student student = requireCurrentStudent();
        return courseRepository.findActiveByDepartmentAndSemester(
                student.getDepartment(), student.getSemester());
    }

    public List<Student> enrolledStudents(Course course) {
        return studentRepository.findByDepartmentAndSemester(
                course.getDepartment(), course.getSemester());
    }

    /**
     * Students visible on the academic profile / eligibility pages.
     * A course filter must be a course the caller can manage.
     */
    public List<Student> accessibleStudents(Long courseId) {
        AuthenticatedUser user = currentUser();
        if (user.role() == Role.STUDENT) {
            return List.of(requireCurrentStudent());
        }
        if (courseId != null) {
            return enrolledStudents(requireManagedCourse(courseId));
        }
        if (user.role() == Role.ADMIN) {
            return studentRepository.findAllActive();
        }
        return studentRepository.findAccessibleByFaculty(requireCurrentFaculty().getId());
    }

    public void assertCanViewStudent(Student student) {
        AuthenticatedUser user = currentUser();
        if (user.role() == Role.ADMIN) {
            return;
        }
        if (user.role() == Role.STUDENT) {
            Student own = requireCurrentStudent();
            if (!Objects.equals(own.getId(), student.getId())) {
                throw new ForbiddenException("You may only view your own academic profile.");
            }
            return;
        }

        Faculty faculty = requireCurrentFaculty();
        boolean assigned = Objects.equals(student.getClassTeacherId(), faculty.getId())
                || Objects.equals(student.getMentorId(), faculty.getId())
                || courseRepository.existsActiveForFacultyAndClass(
                        faculty.getId(), student.getDepartment(), student.getSemester());
        if (!assigned) {
            throw new ForbiddenException(
                    "You may only view students assigned to your subjects or mentoring group.");
        }
    }

    public void assertStudentEnrolled(Course course, Student student) {
        if (student.getStatus() == null || !student.isActive()
                || !Objects.equals(student.getDepartment(), course.getDepartment())
                || student.getSemester() != course.getSemester()) {
            throw new ForbiddenException("This student is not enrolled in the selected subject.");
        }
    }

    public boolean isStudentEnrolled(Course course, Student student) {
        return student.getStatus() != null
                && student.isActive()
                && Objects.equals(student.getDepartment(), course.getDepartment())
                && student.getSemester() == course.getSemester();
    }
}
