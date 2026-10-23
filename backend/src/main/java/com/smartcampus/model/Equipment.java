package com.smartcampus.model;

import java.time.LocalDate;

import com.smartcampus.model.enums.EquipmentCondition;
import com.smartcampus.model.enums.ResourceStatus;

/**
 * A bookable piece of equipment.
 */
public class Equipment extends Resource {

    private String equipmentName;
    private String category;
    private String serialNumber;
    private int quantity;
    private String location;
    private LocalDate purchaseDate;
    private EquipmentCondition condition;
    private String description;

    /** No-argument constructor (Jackson / JDBC row mapping). */
    public Equipment() {
        super();
        this.condition = EquipmentCondition.NEW;
        this.quantity = 1;
    }

    /** Constructor with only the identity fields. */
    public Equipment(Long id, String serialNumber) {
        super(id, ResourceStatus.ACTIVE);
        this.serialNumber = serialNumber;
        this.condition = EquipmentCondition.NEW;
        this.quantity = 1;
    }

    /** Full constructor. */
    public Equipment(Long id, String equipmentName, String category, String serialNumber,
                     int quantity, String location, LocalDate purchaseDate,
                     EquipmentCondition condition, ResourceStatus status, String description) {
        super(id, status);
        this.equipmentName = equipmentName;
        this.category = category;
        this.serialNumber = serialNumber;
        this.quantity = quantity;
        this.location = location;
        this.purchaseDate = purchaseDate;
        this.condition = condition;
        this.description = description;
    }

    @Override
    public String getResourceCode() {
        return serialNumber;
    }

    @Override
    public String getResourceLabel() {
        return equipmentName;
    }

    /** Polymorphic override: adds condition and available quantity to the description. */
    @Override
    public String describeForBooking() {
        return super.describeForBooking() + " (" + condition.getLabel() + ", qty " + quantity + ")";
    }

    /** Quantity currently available (all of it when the item is not under maintenance). */
    public int getAvailableQuantity() {
        // encapsulation: the superclass field is private, so we read it through the getter
        return getStatus() == ResourceStatus.MAINTENANCE ? 0 : quantity;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public EquipmentCondition getCondition() {
        return condition;
    }

    public void setCondition(EquipmentCondition condition) {
        this.condition = condition;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
