package com.smartcampus.model.enums;

/** Physical condition of a piece of equipment. */
public enum EquipmentCondition {

    NEW("New"),
    GOOD("Good"),
    MAINTENANCE_REQUIRED("Maintenance Required"),
    DAMAGED("Damaged");

    private final String label;

    EquipmentCondition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
