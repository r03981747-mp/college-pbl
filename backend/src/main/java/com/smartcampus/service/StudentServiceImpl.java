package com.smartcampus.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.DuplicateRecordException;
import com.smartcampus.exception.ForbiddenException;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.exception.StudentNotFoundException;
import com.smartcampus.model.Student;
import com.smartcampus.model.enums.Role;
import com.smartcampus.model.enums.StudentStatus;
import com.smartcampus.repository.StudentRepository;
import com.smartcampus.security.AuthContext;
import com.smartcampus.security.AuthenticatedUser;
import com.smartcampus.utility.Validators;

/**
 * Student business logic: validation, duplicate detection, paging and audit
 * logging on top of {@link StudentRepository}.
 */
@Service
public class StudentServiceImpl implements StudentService {

    private final StudentRepository repository;
    private final AuditLogService auditLog;

    public StudentServiceImpl(StudentRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    // ==================================================================
    //  Manageable - CRUD
    // ==================================================================

    @Override
    public PagedResponse<Student> list(SearchCriteria criteria) {
        criteria.normalise();
        List<Student> content = repository.search(criteria);
        long total = repository.count(criteria);
        return PagedResponse.of(content, criteria, total);
    }

    @Override
    public Student getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));
    }

    @Override
    public Student create(Student student) {
        validate(student, null);
        ensureUnique(student, null);

        student.setStatus(student.getStatus() == null ? StudentStatus.ACTIVE : student.getStatus());
        Student saved = repository.save(student);

        auditLog.record("CREATE", "STUDENT",
                "Added student " + saved.getUsn() + " (" + saved.getName() + ")");
        return saved;
    }

    @Override
    public Student update(Long id, Student student) {
        Student existing = getById(id);
        student.setId(id);
        // keep the account link when the caller does not send one
        if (student.getUserId() == null) {
            student.setUserId(existing.getUserId());
        }

        validate(student, id);
        ensureUnique(student, id);

        Student saved = repository.update(student);

        auditLog.record("UPDATE", "STUDENT",
                "Updated student " + saved.getUsn() + " (" + saved.getName() + ")");
        return saved;
    }

    @Override
    public void delete(Long id) {
        Student existing = getById(id);
        if (!repository.deleteById(id)) {
            throw new StudentNotFoundException(id);
        }
        auditLog.record("DELETE", "STUDENT",
                "Deleted student " + existing.getUsn() + " (" + existing.getName() + ")");
    }

    // ==================================================================
    //  Searchable
    // ==================================================================

    @Override
    public List<Student> search(String keyword) {
        return search(keyword, SearchCriteria.MAX_PAGE_SIZE);
    }

    @Override
    public List<Student> search(String keyword, int maxResults) {
        SearchCriteria criteria = new SearchCriteria(keyword, 0, Math.max(1, maxResults));
        return repository.search(criteria);
    }

    // ==================================================================
    //  Student specific operations
    // ==================================================================

    @Override
    public Student getByUsn(String usn) {
        String cleaned = Validators.text(usn, "USN");
        return repository.findByUsn(cleaned)
                .orElseThrow(() -> new StudentNotFoundException(cleaned));
    }

    @Override
    public List<String> departments() {
        return repository.findDistinctDepartments();
    }

    @Override
    public Student getOwnProfile() {
        Long userId = AuthContext.userId();
        return repository.findByUserId(userId)
                .orElseThrow(() -> new InvalidInputException(
                        "No student profile is linked to this account yet."));
    }

    @Override
    public Student getVisible(Long studentId) {
        Student student = getById(studentId);

        // students may only open their own record; admins/faculty may open any
        AuthenticatedUser current = AuthContext.get();
        if (current != null && current.role() == Role.STUDENT
                && !Objects.equals(student.getUserId(), current.id())) {
            throw new ForbiddenException("You may only view your own student profile.");
        }
        return student;
    }

    // ==================================================================
    //  Validation helpers
    // ==================================================================

    /** Validates every field of an incoming student payload. */
    private void validate(Student student, Long id) {
        student.setUsn(Validators.requiredText(student.getUsn(), "USN", 20).toUpperCase());
        student.setName(Validators.requiredText(student.getName(), "Full name", 120));
        student.setEmail(Validators.email(student.getEmail(), "Email"));
        student.setPhone(Validators.phone(student.getPhone(), "Phone"));

        if (student.getGender() == null) {
            throw new InvalidInputException("Gender: is required");
        }
        Validators.notInFuture(student.getDateOfBirth(), "Date of birth");

        student.setDepartment(Validators.requiredText(student.getDepartment(), "Department", 60));
        Validators.range(student.getSemester(), 1, 8, "Semester");
        Validators.range(student.getAdmissionYear(), 2000,
                LocalDate.now().getYear() + 1, "Admission year");

        if (student.getSection() == null || student.getSection().isBlank()) {
            student.setSection("A");
        } else {
            student.setSection(student.getSection().trim().toUpperCase());
            if (student.getSection().length() > 5) {
                throw new InvalidInputException("Section", "must be at most 5 characters");
            }
        }

        student.setAddress(Validators.optionalText(student.getAddress(), "Address", 255));

        if (student.getStatus() == null) {
            student.setStatus(StudentStatus.ACTIVE);
        }
    }

    /** Unique USN and e-mail checks with a friendly 409 message. */
    private void ensureUnique(Student student, Long id) {
        if (repository.existsByUsn(student.getUsn(), id)) {
            throw new DuplicateRecordException("USN", student.getUsn());
        }
        if (repository.existsByEmail(student.getEmail(), id)) {
            throw new DuplicateRecordException("Email", student.getEmail());
        }
    }
}
