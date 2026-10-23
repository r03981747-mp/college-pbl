package com.smartcampus.model;

import java.time.LocalDate;

import com.smartcampus.model.enums.FacultyStatus;

/**
 * A faculty member of the college.
 *
 * <p><b>OOP concepts:</b> <i>inheritance</i> (extends {@link Person}),
 * <i>encapsulation</i>, <i>constructor overloading</i> and
 * <i>method overriding</i> ({@link #getProfileType()}).</p>
 */
public class Faculty extends Person {

    private String employeeId;
    private String designation;
    private String qualification;
    private LocalDate joiningDate;
    private FacultyStatus status;
    private Long userId;

    /** No-argument constructor (Jackson / JDBC row mapping). */
    public Faculty() {
        super();
        this.status = FacultyStatus.ACTIVE;
    }

    /** Partial constructor - only identity fields. */
    public Faculty(Long id, String employeeId, String name) {
        super(id, name, null, null, null);
        this.employeeId = employeeId;
        this.status = FacultyStatus.ACTIVE;
    }

    /** Full constructor - used when building a record from an incoming DTO. */
    public Faculty(Long id, String employeeId, String name, String email, String phone,
                   String department, String designation, String qualification,
                   LocalDate joiningDate, FacultyStatus status, Long userId) {
        super(id, name, email, phone, department);
        this.employeeId = employeeId;
        this.designation = designation;
        this.qualification = qualification;
        this.joiningDate = joiningDate;
        this.status = status;
        this.userId = userId;
    }

    /** Polymorphic answer to {@link Person#getProfileType()}. */
    @Override
    public String getProfileType() {
        return "Faculty";
    }

    @Override
    public String getDisplayName() {
        return getEmployeeId() + " - " + getName();
    }

    /** Years of service in the college. */
    public long getYearsOfService() {
        if (joiningDate == null) {
            return 0;
        }
        return java.time.Period.between(joiningDate, LocalDate.now()).getYears();
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getQualification() {
        return qualification;
    }

    public void setQualification(String qualification) {
        this.qualification = qualification;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(LocalDate joiningDate) {
        this.joiningDate = joiningDate;
    }

    public FacultyStatus getStatus() {
        return status;
    }

    public void setStatus(FacultyStatus status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "Faculty{" + employeeId + ", " + getName() + ", " + designation + "}";
    }
}
