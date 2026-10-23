package com.smartcampus.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.AvailabilityRequest;
import com.smartcampus.dto.AvailabilityResponse;
import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.DuplicateRecordException;
import com.smartcampus.exception.ResourceNotFoundException;
import com.smartcampus.model.Classroom;
import com.smartcampus.model.enums.ResourceStatus;
import com.smartcampus.model.enums.ResourceType;
import com.smartcampus.repository.ClassroomRepository;
import com.smartcampus.utility.Validators;

/**
 * Classroom module: validation, duplicate detection, paging and availability.
 */
@Service
public class ClassroomService implements ResourceService<Classroom> {

    private final ClassroomRepository repository;
    private final BookingService bookingService;
    private final AuditLogService auditLog;

    public ClassroomService(ClassroomRepository repository,
                            BookingService bookingService,
                            AuditLogService auditLog) {
        this.repository = repository;
        this.bookingService = bookingService;
        this.auditLog = auditLog;
    }

    // ==================================================================
    //  Manageable - CRUD
    // ==================================================================

    @Override
    public PagedResponse<Classroom> list(SearchCriteria criteria) {
        criteria.normalise();
        List<Classroom> content = repository.search(criteria);
        long total = repository.count(criteria);
        return PagedResponse.of(content, criteria, total);
    }

    @Override
    public Classroom getById(Long id) {
        return requireResource(id);
    }

    @Override
    public Classroom create(Classroom classroom) {
        validate(classroom);
        ensureUnique(classroom, null);

        classroom.setStatus(classroom.getStatus() == null
                ? ResourceStatus.ACTIVE : classroom.getStatus());
        Classroom saved = repository.save(classroom);

        auditLog.record("CREATE", "CLASSROOM", "Added room " + saved.getRoomNumber());
        return saved;
    }

    @Override
    public Classroom update(Long id, Classroom classroom) {
        requireResource(id);
        classroom.setId(id);

        validate(classroom);
        ensureUnique(classroom, id);

        Classroom saved = repository.update(classroom);
        auditLog.record("UPDATE", "CLASSROOM", "Updated room " + saved.getRoomNumber());
        return saved;
    }

    @Override
    public void delete(Long id) {
        Classroom existing = requireResource(id);
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException(id);
        }
        auditLog.record("DELETE", "CLASSROOM", "Deleted room " + existing.getRoomNumber());
    }

    // ==================================================================
    //  Searchable
    // ==================================================================

    @Override
    public List<Classroom> search(String keyword) {
        return search(keyword, SearchCriteria.MAX_PAGE_SIZE);
    }

    @Override
    public List<Classroom> search(String keyword, int maxResults) {
        return repository.search(new SearchCriteria(keyword, 0, Math.max(1, maxResults)));
    }

    // ==================================================================
    //  ResourceService
    // ==================================================================

    @Override
    public ResourceType getResourceType() {
        return ResourceType.CLASSROOM;
    }

    @Override
    public Classroom requireResource(Long resourceId) {
        if (resourceId == null) {
            throw new ResourceNotFoundException(ResourceType.CLASSROOM, null);
        }
        return repository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.CLASSROOM, resourceId));
    }

    @Override
    public Classroom requireBookable(Long resourceId) {
        Classroom classroom = requireResource(resourceId);
        if (!classroom.isBookable()) {
            throw ResourceNotFoundException.notBookable(
                    classroom.getResourceLabel(), classroom.getStatus().getLabel());
        }
        return classroom;
    }

    @Override
    public AvailabilityResponse checkAvailability(AvailabilityRequest request) {
        request.setResourceType(ResourceType.CLASSROOM);
        return bookingService.checkAvailability(request);
    }

    /** Distinct buildings - fills the filter drop-down. */
    public List<String> buildings() {
        return repository.findDistinctBuildings();
    }

    /** Look-up by room number (used by the booking screen). */
    public Classroom byRoomNumber(String roomNumber) {
        return repository.findByRoomNumber(Validators.text(roomNumber, "Room number"))
                .orElseThrow(() -> new ResourceNotFoundException(roomNumber));
    }

    // ==================================================================
    //  Validation
    // ==================================================================

    private void validate(Classroom classroom) {
        classroom.setRoomNumber(Validators.requiredText(
                classroom.getRoomNumber(), "Room number", 20).toUpperCase());
        classroom.setBuilding(Validators.requiredText(classroom.getBuilding(), "Building", 60));
        Validators.range(classroom.getFloor(), 0, 50, "Floor");
        Validators.atLeast(classroom.getCapacity(), 1, "Capacity");

        if (classroom.getType() == null) {
            throw new com.smartcampus.exception.InvalidInputException("Type", "is required");
        }
        classroom.setFacilities(Validators.optionalText(
                classroom.getFacilities(), "Facilities", 255));

        if (classroom.getStatus() == null) {
            classroom.setStatus(ResourceStatus.ACTIVE);
        }
    }

    private void ensureUnique(Classroom classroom, Long id) {
        if (repository.existsByRoomNumber(classroom.getRoomNumber(), id)) {
            throw new DuplicateRecordException("Room number", classroom.getRoomNumber());
        }
    }
}
