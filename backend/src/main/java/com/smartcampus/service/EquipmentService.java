package com.smartcampus.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.AvailabilityRequest;
import com.smartcampus.dto.AvailabilityResponse;
import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.DuplicateRecordException;
import com.smartcampus.exception.ResourceNotFoundException;
import com.smartcampus.model.Equipment;
import com.smartcampus.model.enums.EquipmentCondition;
import com.smartcampus.model.enums.ResourceStatus;
import com.smartcampus.model.enums.ResourceType;
import com.smartcampus.repository.EquipmentRepository;
import com.smartcampus.utility.Validators;

/**
 * Equipment module: validation, duplicate detection, paging, availability and
 * quick updates of condition / quantity.
 */
@Service
public class EquipmentService implements ResourceService<Equipment> {

    private final EquipmentRepository repository;
    private final BookingService bookingService;
    private final AuditLogService auditLog;

    public EquipmentService(EquipmentRepository repository,
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
    public PagedResponse<Equipment> list(SearchCriteria criteria) {
        criteria.normalise();
        List<Equipment> content = repository.search(criteria);
        long total = repository.count(criteria);
        return PagedResponse.of(content, criteria, total);
    }

    @Override
    public Equipment getById(Long id) {
        return requireResource(id);
    }

    @Override
    public Equipment create(Equipment equipment) {
        validate(equipment);
        ensureUnique(equipment, null);

        equipment.setStatus(equipment.getStatus() == null
                ? ResourceStatus.ACTIVE : equipment.getStatus());
        Equipment saved = repository.save(equipment);

        auditLog.record("CREATE", "EQUIPMENT",
                "Added equipment " + saved.getSerialNumber() + " (" + saved.getEquipmentName() + ")");
        return saved;
    }

    @Override
    public Equipment update(Long id, Equipment equipment) {
        requireResource(id);
        equipment.setId(id);

        validate(equipment);
        ensureUnique(equipment, id);

        Equipment saved = repository.update(equipment);
        auditLog.record("UPDATE", "EQUIPMENT",
                "Updated equipment " + saved.getSerialNumber() + " (" + saved.getEquipmentName() + ")");
        return saved;
    }

    @Override
    public void delete(Long id) {
        Equipment existing = requireResource(id);
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException(id);
        }
        auditLog.record("DELETE", "EQUIPMENT",
                "Deleted equipment " + existing.getSerialNumber());
    }

    // ==================================================================
    //  Searchable
    // ==================================================================

    @Override
    public List<Equipment> search(String keyword) {
        return search(keyword, SearchCriteria.MAX_PAGE_SIZE);
    }

    @Override
    public List<Equipment> search(String keyword, int maxResults) {
        return repository.search(new SearchCriteria(keyword, 0, Math.max(1, maxResults)));
    }

    // ==================================================================
    //  ResourceService
    // ==================================================================

    @Override
    public ResourceType getResourceType() {
        return ResourceType.EQUIPMENT;
    }

    @Override
    public Equipment requireResource(Long resourceId) {
        if (resourceId == null) {
            throw new ResourceNotFoundException(ResourceType.EQUIPMENT, null);
        }
        return repository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.EQUIPMENT, resourceId));
    }

    @Override
    public Equipment requireBookable(Long resourceId) {
        Equipment equipment = requireResource(resourceId);
        if (!equipment.isBookable()) {
            throw ResourceNotFoundException.notBookable(
                    equipment.getResourceLabel(), equipment.getStatus().getLabel());
        }
        return equipment;
    }

    @Override
    public AvailabilityResponse checkAvailability(AvailabilityRequest request) {
        request.setResourceType(ResourceType.EQUIPMENT);
        return bookingService.checkAvailability(request);
    }

    // ==================================================================
    //  Quick operations offered by the UI
    // ==================================================================

    /** Updates only the physical condition ("Update condition" action). */
    public Equipment updateCondition(Long id, EquipmentCondition condition) {
        Equipment equipment = requireResource(id);
        if (condition == null) {
            throw new com.smartcampus.exception.InvalidInputException("Condition", "is required");
        }
        repository.updateCondition(id, condition);

        auditLog.record("UPDATE", "EQUIPMENT",
                equipment.getSerialNumber() + " condition -> " + condition.getLabel());
        return requireResource(id);
    }

    /** Updates only the stock quantity ("Track quantity" action). */
    public Equipment updateQuantity(Long id, int quantity) {
        Equipment equipment = requireResource(id);
        Validators.atLeast(quantity, 0, "Quantity");

        repository.updateQuantity(id, quantity);
        auditLog.record("UPDATE", "EQUIPMENT",
                equipment.getSerialNumber() + " quantity -> " + quantity);
        return requireResource(id);
    }

    public List<String> categories() {
        return repository.findDistinctCategories();
    }

    public List<String> locations() {
        return repository.findDistinctLocations();
    }

    /** Look-up by serial number (used by the booking screen). */
    public Equipment bySerialNumber(String serialNumber) {
        return repository.findBySerialNumber(Validators.text(serialNumber, "Serial number"))
                .orElseThrow(() -> new ResourceNotFoundException(serialNumber));
    }

    // ==================================================================
    //  Validation
    // ==================================================================

    private void validate(Equipment equipment) {
        equipment.setEquipmentName(Validators.requiredText(
                equipment.getEquipmentName(), "Equipment name", 120));
        equipment.setCategory(Validators.requiredText(equipment.getCategory(), "Category", 60));
        equipment.setSerialNumber(Validators.requiredText(
                equipment.getSerialNumber(), "Serial number", 60).toUpperCase());
        Validators.atLeast(equipment.getQuantity(), 0, "Quantity");
        Validators.range(equipment.getQuantity(), 0, 100_000, "Quantity");
        equipment.setLocation(Validators.requiredText(equipment.getLocation(), "Location", 120));

        if (equipment.getPurchaseDate() != null) {
            Validators.notInFuture(equipment.getPurchaseDate(), "Purchase date");
        }
        if (equipment.getCondition() == null) {
            equipment.setCondition(EquipmentCondition.NEW);
        }
        if (equipment.getStatus() == null) {
            equipment.setStatus(ResourceStatus.ACTIVE);
        }
        equipment.setDescription(Validators.optionalText(
                equipment.getDescription(), "Description", 500));
    }

    private void ensureUnique(Equipment equipment, Long id) {
        if (repository.existsBySerialNumber(equipment.getSerialNumber(), id)) {
            throw new DuplicateRecordException("Serial number", equipment.getSerialNumber());
        }
    }
}
