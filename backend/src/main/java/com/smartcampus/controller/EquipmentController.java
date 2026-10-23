package com.smartcampus.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartcampus.dto.ApiResponse;
import com.smartcampus.dto.AvailabilityRequest;
import com.smartcampus.dto.AvailabilityResponse;
import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.model.Equipment;
import com.smartcampus.model.enums.EquipmentCondition;
import com.smartcampus.model.enums.ResourceType;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.EquipmentService;

/**
 * REST API of the equipment module.
 *
 * <pre>
 * GET    /api/equipment                list + search + filter + sort + page
 * GET    /api/equipment/search         keyword search
 * GET    /api/equipment/options        categories / locations
 * GET    /api/equipment/{id}           one record
 * POST   /api/equipment                create   201
 * PUT    /api/equipment/{id}           update   200
 * PATCH  /api/equipment/{id}/condition update condition      200
 * PATCH  /api/equipment/{id}/quantity  update quantity       200
 * DELETE /api/equipment/{id}           delete   200
 * POST   /api/equipment/{id}/availability  "is it free?" check
 * </pre>
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    /** Body of the "update condition" action. */
    public record ConditionRequest(EquipmentCondition condition) {
    }

    /** Body of the "update quantity" action. */
    public record QuantityRequest(int quantity) {
    }

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<Equipment>>> list(
            @RequestParam Map<String, String> params) {
        PagedResponse<Equipment> page = equipmentService.list(SearchCriteria.from(params));
        return ResponseEntity.ok(ApiResponse.ok(page,
                page.getTotalElements() + " equipment record(s) found"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Equipment>>> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        List<Equipment> results = equipmentService.search(keyword, limit);
        return ResponseEntity.ok(ApiResponse.ok(results, results.size() + " match(es)"));
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> options() {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("categories", equipmentService.categories(),
                       "locations", equipmentService.locations()),
                "Filter options"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Equipment>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(equipmentService.getById(id), "Equipment found"));
    }

    @PostMapping
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Equipment>> create(@RequestBody Equipment equipment) {
        Equipment saved = equipmentService.create(equipment);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(saved, "Equipment added successfully."));
    }

    @PutMapping("/{id}")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Equipment>> update(
            @PathVariable Long id, @RequestBody Equipment equipment) {
        Equipment saved = equipmentService.update(id, equipment);
        return ResponseEntity.ok(ApiResponse.ok(saved, "Equipment updated successfully."));
    }

    /** Quick action: change only the physical condition of an item. */
    @PutMapping("/{id}/condition")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Equipment>> updateCondition(
            @PathVariable Long id, @RequestBody ConditionRequest body) {
        Equipment saved = equipmentService.updateCondition(id, body.condition());
        return ResponseEntity.ok(ApiResponse.ok(saved,
                "Condition updated to " + saved.getCondition().getLabel()));
    }

    /** Quick action: change only the stock quantity of an item. */
    @PutMapping("/{id}/quantity")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Equipment>> updateQuantity(
            @PathVariable Long id, @RequestBody QuantityRequest body) {
        Equipment saved = equipmentService.updateQuantity(id, body.quantity());
        return ResponseEntity.ok(ApiResponse.ok(saved,
                "Quantity updated to " + saved.getQuantity()));
    }

    @DeleteMapping("/{id}")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        equipmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Equipment deleted successfully."));
    }

    /** "Check availability" action on the equipment page. */
    @PostMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> availability(
            @PathVariable Long id, @RequestBody AvailabilityRequest request) {
        request.setResourceId(id);
        request.setResourceType(ResourceType.EQUIPMENT);
        AvailabilityResponse availability = equipmentService.checkAvailability(request);
        return ResponseEntity.ok(ApiResponse.ok(availability, availability.getMessage()));
    }
}
