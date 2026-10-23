package com.smartcampus.model;

import com.smartcampus.model.enums.ResourceStatus;

/**
 * A bookable laboratory.
 */
public class Laboratory extends Resource {

    private String labNumber;
    private String labName;
    private String building;
    private String department;
    private int capacity;
    private String labType;
    private int equipmentCount;
    private String description;

    /** No-argument constructor (Jackson / JDBC row mapping). */
    public Laboratory() {
        super();
    }

    /** Constructor with only the identity fields. */
    public Laboratory(Long id, String labNumber) {
        super(id, ResourceStatus.ACTIVE);
        this.labNumber = labNumber;
    }

    /** Full constructor. */
    public Laboratory(Long id, String labNumber, String labName, String building,
                      String department, int capacity, String labType, int equipmentCount,
                      ResourceStatus status, String description) {
        super(id, status);
        this.labNumber = labNumber;
        this.labName = labName;
        this.building = building;
        this.department = department;
        this.capacity = capacity;
        this.labType = labType;
        this.equipmentCount = equipmentCount;
        this.description = description;
    }

    @Override
    public String getResourceCode() {
        return labNumber;
    }

    @Override
    public String getResourceLabel() {
        return labNumber + " - " + labName;
    }

    /** Polymorphic override: adds the owning department to the booking description. */
    @Override
    public String describeForBooking() {
        return super.describeForBooking() + " [" + department + "]";
    }

    public String getLabNumber() {
        return labNumber;
    }

    public void setLabNumber(String labNumber) {
        this.labNumber = labNumber;
    }

    public String getLabName() {
        return labName;
    }

    public void setLabName(String labName) {
        this.labName = labName;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getLabType() {
        return labType;
    }

    public void setLabType(String labType) {
        this.labType = labType;
    }

    public int getEquipmentCount() {
        return equipmentCount;
    }

    public void setEquipmentCount(int equipmentCount) {
        this.equipmentCount = equipmentCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
