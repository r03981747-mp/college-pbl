package com.smartcampus.model.enums;

/** Status of a user account (login enabled / disabled). */
public enum AccountStatus {

    ACTIVE("Active"),
    INACTIVE("Inactive");

    private final String label;

    AccountStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isEnabled() {
        return this == ACTIVE;
    }

    @Override
    public String toString() {
        return label;
    }
}
