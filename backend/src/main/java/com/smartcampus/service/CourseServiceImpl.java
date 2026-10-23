package com.smartcampus.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.CourseNotFoundException;
import com.smartcampus.exception.DuplicateRecordException;
import com.smartcampus.exception.FacultyNotFoundException;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.model.Course;
import com.smartcampus.model.Faculty;
import com.smartcampus.model.enums.CourseStatus;
import com.smartcampus.repository.CourseRepository;
import com.smartcampus.repository.FacultyRepository;
import com.smartcampus.utility.Validators;

/**
 * Course business logic: validation, duplicate detection, faculty assignment
 * and paging on top of {@link CourseRepository}.
 */
@Service
public class CourseServiceImpl implements CourseService {

    private final CourseRepository repository;
    private final FacultyRepository facultyRepository;
    private final AuditLogService auditLog;

    public CourseServiceImpl(CourseRepository repository,
                             FacultyRepository facultyRepository,
                             AuditLogService auditLog) {
        this.repository = repository;
        this.facultyRepository = facultyRepository;
        this.auditLog = auditLog;
    }

    // ==================================================================
    //  Manageable - CRUD
    // ==================================================================

    @Override
    public PagedResponse<Course> list(SearchCriteria criteria) {
        criteria.normalise();
        List<Course> content = repository.search(criteria);
        long total = repository.count(criteria);
        return PagedResponse.of(content, criteria, total);
    }

    @Override
    public Course getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException(id));
    }

    @Override
    public Course create(Course course) {
        validate(course);
        ensureUnique(course, null);
        ensureFacultyExists(course.getFacultyId());

        course.setStatus(course.getStatus() == null ? CourseStatus.ACTIVE : course.getStatus());
        Course saved = repository.save(course);

        auditLog.record("CREATE", "COURSE",
                "Added course " + saved.getCourseCode() + " (" + saved.getCourseName() + ")");
        return saved;
    }

    @Override
    public Course update(Long id, Course course) {
        getById(id);
        course.setId(id);

        validate(course);
        ensureUnique(course, id);
        ensureFacultyExists(course.getFacultyId());

        Course saved = repository.update(course);

        auditLog.record("UPDATE", "COURSE",
                "Updated course " + saved.getCourseCode() + " (" + saved.getCourseName() + ")");
        return saved;
    }

    @Override
    public void delete(Long id) {
        Course existing = getById(id);
        if (!repository.deleteById(id)) {
            throw new CourseNotFoundException(id);
        }
        auditLog.record("DELETE", "COURSE",
                "Deleted course " + existing.getCourseCode() + " (" + existing.getCourseName() + ")");
    }

    // ==================================================================
    //  Searchable
    // ==================================================================

    @Override
    public List<Course> search(String keyword) {
        return search(keyword, SearchCriteria.MAX_PAGE_SIZE);
    }

    @Override
    public List<Course> search(String keyword, int maxResults) {
        return repository.search(new SearchCriteria(keyword, 0, Math.max(1, maxResults)));
    }

    // ==================================================================
    //  Course specific operations
    // ==================================================================

    @Override
    public Course getByCode(String courseCode) {
        String cleaned = Validators.text(courseCode, "Course code");
        return repository.findAll().stream()
                .filter(course -> course.getCourseCode().equalsIgnoreCase(cleaned))
                .findFirst()
                .orElseThrow(() -> new CourseNotFoundException(cleaned));
    }

    @Override
    public List<String> departments() {
        return repository.findDistinctDepartments();
    }

    @Override
    public Course assignFaculty(Long courseId, Long facultyId) {
        Course course = getById(courseId);
        ensureFacultyExists(facultyId);

        if (!repository.assignFaculty(courseId, facultyId)) {
            throw new CourseNotFoundException(courseId);
        }

        // reload so the response carries the updated faculty name (JOIN)
        Course updated = getById(courseId);

        String assignedTo = facultyId == null
                ? "nobody"
                : facultyRepository.findById(facultyId).map(Faculty::getName).orElse("faculty #" + facultyId);
        auditLog.record("UPDATE", "COURSE",
                "Assigned " + course.getCourseCode() + " to " + assignedTo);

        return updated;
    }

    @Override
    public List<Course> findUnassigned() {
        return repository.findUnassignedCourses();
    }

    // ==================================================================
    //  Validation helpers
    // ==================================================================

    private void validate(Course course) {
        course.setCourseCode(Validators.requiredText(
                course.getCourseCode(), "Course code", 20).toUpperCase());
        course.setCourseName(Validators.requiredText(course.getCourseName(), "Course name", 150));
        course.setDepartment(Validators.requiredText(course.getDepartment(), "Department", 60));
        Validators.range(course.getSemester(), 1, 8, "Semester");
        Validators.range(course.getCredits(), 1, 10, "Credits");
        course.setDescription(Validators.optionalText(course.getDescription(), "Description", 500));

        if (course.getStatus() == null) {
            course.setStatus(CourseStatus.ACTIVE);
        }
    }

    private void ensureUnique(Course course, Long id) {
        if (repository.existsByCourseCode(course.getCourseCode(), id)) {
            throw new DuplicateRecordException("Course code", course.getCourseCode());
        }
    }

    /** A course may be unassigned (null), but an id must point at a real faculty row. */
    private void ensureFacultyExists(Long facultyId) {
        if (facultyId == null) {
            return;
        }
        Optional<Faculty> faculty = facultyRepository.findById(facultyId);
        if (faculty.isEmpty()) {
            throw new FacultyNotFoundException(facultyId);
        }
        if (faculty.get().getStatus() == com.smartcampus.model.enums.FacultyStatus.INACTIVE) {
            throw new InvalidInputException("Faculty",
                    "cannot be assigned to a course while inactive");
        }
    }
}
