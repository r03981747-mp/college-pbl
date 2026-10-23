package com.smartcampus.model;

/**
 * Abstract superclass of {@link Student} and {@link Faculty}.
 *
 * <p><b>OOP concepts:</b> <i>abstraction</i> (an abstract class that cannot be
 * instantiated), <i>inheritance</i> (both subclasses reuse the four common
 * fields) and <i>polymorphism</i> ({@link #getProfileType()} is overridden by
 * each subclass).</p>
 *
 * @author Smart Campus PBL
 */
public abstract class Person extends BaseEntity {

    private String name;
    private String email;
    private String phone;
    private String department;

    /** Default constructor. */
    public Person() {
        super();
    }

    /** Constructor used when only the primary key is known. */
    public Person(Long id) {
        super(id);
    }

    /** Full constructor. */
    public Person(Long id, String name, String email, String phone, String department) {
        super(id);
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.department = department;
    }

    /**
     * Abstract method - every concrete subclass MUST provide its own answer.
     * This is the polymorphic entry point used by generic person handling code.
     *
     * @return a short label such as {@code "Student"} or {@code "Faculty"}
     */
    public abstract String getProfileType();

    /** Human friendly display name (overridable by subclasses). */
    public String getDisplayName() {
        return name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    @Override
    public String toString() {
        return getProfileType() + "{" + name + ", " + email + "}";
    }
}
