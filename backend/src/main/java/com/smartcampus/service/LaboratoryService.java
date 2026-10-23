package com.smartcampus.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.AvailabilityRequest;
import com.smartcampus.dto.AvailabilityResponse;
import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.DuplicateRecordException;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.exception.ResourceNotFoundException;
import com.smartcampus.model.Laboratory;
import com.smartcampus.model.enums.ResourceStatus;
import com.smartcampus.model.enums.ResourceType;
import com.smartcampus.repository.LaboratoryRepository;
import com.smartcampus.utility.Validators;

/**
 * Laboratory module: validation, duplicate detection, paging and availability.
 */
@Service
public class LaboratoryService implements ResourceService<Laboratory> {

    private final LaboratoryRepository repository;
    private final BookingService bookingService;
    private final AuditLogService auditLog;

    public LaboratoryService(LaboratoryRepository repository,
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
    public PagedResponse<Laboratory> list(SearchCriteria criteria) {
        criteria.normalise();
        List<Laboratory> content = repository.search(criteria);
        long total = repository.count(criteria);
        return PagedResponse.of(content, criteria, total);
    }

    @Override
    public Laboratory getById(Long id) {
        return requireResource(id);
    }

    @Override
    public Laboratory create(Laboratory laboratory) {
        validate(laboratory);
        ensureUnique(laboratory, null);

        laboratory.setStatus(laboratory.getStatus() == null
                ? ResourceStatus.ACTIVE : laboratory.getStatus());
        Laboratory saved = repository.save(laboratory);

        auditLog.record("CREATE", "LABORATORY", "Added lab " + saved.getLabNumber());
        return saved;
    }

    @Override
    public Laboratory update(Long id, Laboratory laboratory) {
        requireResource(id);
        laboratory.setId(id);

        validate(laboratory);
        ensureUnique(laboratory, id);

        Laboratory saved = repository.update(laboratory);
        auditLog.record("UPDATE", "LABORATORY", "Updated lab " + saved.getLabNumber());
        return saved;
    }

    @Override
    public void delete(Long id) {
        Laboratory existing = requireResource(id);
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException(id);
        }
        auditLog.record("DELETE", "LABORATORY", "Deleted lab " + existing.getLabNumber());
    }

    // ==================================================================
    //  Searchable
    // ==================================================================

    @Override
    public List<Laboratory> search(String keyword) {
        return search(keyword, SearchCriteria.MAX_PAGE_SIZE);
    }

    @Override
    public List<Laboratory> search(String keyword, int maxResults) {
        return repository.search(new SearchCriteria(keyword, 0, Math.max(1, maxResults)));
    }

    // ==================================================================
    //  ResourceService
    // ==================================================================

    @Override
    public ResourceType getResourceType() {
        return ResourceType.LABORATORY;
    }

    @Override
    public Laboratory requireResource(Long resourceId) {
        if (resourceId == null) {
            throw new ResourceNotFoundException(ResourceType.LABORATORY, null);
        }
        return repository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.LABORATORY, resourceId));
    }

    @Override
    public Laboratory requireBookable(Long resourceId) {
        Laboratory laboratory = requireResource(resourceId);
        if (!laboratory.isBookable()) {
            throw ResourceNotFoundException.notBookable(
                    laboratory.getResourceLabel(), laboratory.getStatus().getLabel());
        }
        return laboratory;
    }

    @Override
    public AvailabilityResponse checkAvailability(AvailabilityRequest request) {
        request.setResourceType(ResourceType.LABORATORY);
        return bookingService.checkAvailability(request);
    }

    public List<String> departments() {
        return repository.findDistinctDepartments();
    }

    public List<String> labTypes() {
        return repository.findDistinctLabTypes();
    }

    /** Look-up by lab number (used by the booking screen). */
    public Laboratory byLabNumber(String labNumber) {
        return repository.findByLabNumber(Validators.text(labNumber, "Lab number"))
                .orElseThrow(() -> new ResourceNotFoundException(labNumber));
    }

    // ==================================================================
    //  Validation
    // ==================================================================

    private void validate(Laboratory laboratory) {
        laboratory.setLabNumber(Validators.requiredText(
                laboratory.getLabNumber(), "Lab number", 20).toUpperCase());
        laboratory.setLabName(Validators.requiredText(laboratory.getLabName(), "Lab name", 120));
        laboratory.setBuilding(Validators.requiredText(laboratory.getBuilding(), "Building", 60));
        laboratory.setDepartment(Validators.requiredText(
                laboratory.getDepartment(), "Department", 60));
        Validators.atLeast(laboratory.getCapacity(), 1, "Capacity");
        laboratory.setLabType(Validators.requiredText(laboratory.getLabType(), "Lab type", 40));
        Validators.atLeast(laboratory.getEquipmentCount(), 0, "Equipment count");
        laboratory.setDescription(Validators.optionalText(
                laboratory.getDescription(), "Description", 500));

        if (laboratory.getStatus() == null) {
            laboratory.setStatus(ResourceStatus.ACTIVE);
        }
        if (laboratory.getEquipmentCount() > laboratory.getCapacity() * 4) {
            throw new InvalidInputException("Equipment count",
                    "looks unrealistic for a lab of this size");
        }
    }

    private void ensureUnique(Laboratory laboratory, Long id) {
        if (repository.existsByLabNumber(laboratory.getLabNumber(), id)) {
            throw new DuplicateRecordException("Lab number", laboratory.getLabNumber());
        }
    }
}
