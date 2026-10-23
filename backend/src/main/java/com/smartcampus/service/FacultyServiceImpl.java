package com.smartcampus.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.DuplicateRecordException;
import com.smartcampus.exception.FacultyNotFoundException;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.model.Faculty;
import com.smartcampus.model.enums.FacultyStatus;
import com.smartcampus.repository.FacultyRepository;
import com.smartcampus.security.AuthContext;
import com.smartcampus.utility.Validators;

/**
 * Faculty business logic: validation, duplicate detection, paging and audit
 * logging on top of {@link FacultyRepository}.
 */
@Service
public class FacultyServiceImpl implements FacultyService {

    private final FacultyRepository repository;
    private final AuditLogService auditLog;

    public FacultyServiceImpl(FacultyRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    // ==================================================================
    //  Manageable - CRUD
    // ==================================================================

    @Override
    public PagedResponse<Faculty> list(SearchCriteria criteria) {
        criteria.normalise();
        List<Faculty> content = repository.search(criteria);
        long total = repository.count(criteria);
        return PagedResponse.of(content, criteria, total);
    }

    @Override
    public Faculty getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new FacultyNotFoundException(id));
    }

    @Override
    public Faculty create(Faculty faculty) {
        validate(faculty);
        ensureUnique(faculty, null);

        faculty.setStatus(faculty.getStatus() == null ? FacultyStatus.ACTIVE : faculty.getStatus());
        Faculty saved = repository.save(faculty);

        auditLog.record("CREATE", "FACULTY",
                "Added faculty " + saved.getEmployeeId() + " (" + saved.getName() + ")");
        return saved;
    }

    @Override
    public Faculty update(Long id, Faculty faculty) {
        Faculty existing = getById(id);
        faculty.setId(id);
        if (faculty.getUserId() == null) {
            faculty.setUserId(existing.getUserId());
        }

        validate(faculty);
        ensureUnique(faculty, id);

        Faculty saved = repository.update(faculty);

        auditLog.record("UPDATE", "FACULTY",
                "Updated faculty " + saved.getEmployeeId() + " (" + saved.getName() + ")");
        return saved;
    }

    @Override
    public void delete(Long id) {
        Faculty existing = getById(id);
        if (!repository.deleteById(id)) {
            throw new FacultyNotFoundException(id);
        }
        auditLog.record("DELETE", "FACULTY",
                "Deleted faculty " + existing.getEmployeeId() + " (" + existing.getName() + ")");
    }

    // ==================================================================
    //  Searchable
    // ==================================================================

    @Override
    public List<Faculty> search(String keyword) {
        return search(keyword, SearchCriteria.MAX_PAGE_SIZE);
    }

    @Override
    public List<Faculty> search(String keyword, int maxResults) {
        return repository.search(new SearchCriteria(keyword, 0, Math.max(1, maxResults)));
    }

    // ==================================================================
    //  Faculty specific operations
    // ==================================================================

    @Override
    public Faculty getByEmployeeId(String employeeId) {
        String cleaned = Validators.text(employeeId, "Employee ID");
        return repository.findAll().stream()
                .filter(member -> member.getEmployeeId().equalsIgnoreCase(cleaned))
                .findFirst()
                .orElseThrow(() -> new FacultyNotFoundException(cleaned));
    }

    @Override
    public List<String> departments() {
        return repository.findDistinctDepartments();
    }

    @Override
    public List<String> designations() {
        return repository.findDistinctDesignations();
    }

    @Override
    public Faculty getOwnProfile() {
        Long userId = AuthContext.userId();
        Optional<Faculty> profile = repository.findByUserId(userId);
        if (profile.isEmpty()) {
            throw new InvalidInputException(
                    "No faculty profile is linked to this account yet.");
        }
        return profile.get();
    }

    // ==================================================================
    //  Validation helpers
    // ==================================================================

    private void validate(Faculty faculty) {
        faculty.setEmployeeId(Validators.requiredText(
                faculty.getEmployeeId(), "Employee ID", 20).toUpperCase());
        faculty.setName(Validators.requiredText(faculty.getName(), "Name", 120));
        faculty.setEmail(Validators.email(faculty.getEmail(), "Email"));
        faculty.setPhone(Validators.phone(faculty.getPhone(), "Phone"));
        faculty.setDepartment(Validators.requiredText(faculty.getDepartment(), "Department", 60));
        faculty.setDesignation(Validators.requiredText(faculty.getDesignation(), "Designation", 60));
        faculty.setQualification(Validators.optionalText(
                faculty.getQualification(), "Qualification", 120));
        Validators.notInFuture(faculty.getJoiningDate(), "Joining date");

        if (faculty.getStatus() == null) {
            faculty.setStatus(FacultyStatus.ACTIVE);
        }
    }

    private void ensureUnique(Faculty faculty, Long id) {
        if (repository.existsByEmployeeId(faculty.getEmployeeId(), id)) {
            throw new DuplicateRecordException("Employee ID", faculty.getEmployeeId());
        }
        if (repository.existsByEmail(faculty.getEmail(), id)) {
            throw new DuplicateRecordException("Email", faculty.getEmail());
        }
    }
}
