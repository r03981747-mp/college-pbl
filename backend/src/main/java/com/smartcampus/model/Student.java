package com.smartcampus.model;

import java.time.LocalDate;
import java.time.Period;

import com.smartcampus.model.enums.Gender;
import com.smartcampus.model.enums.StudentStatus;

/**
 * A student enrolled in the college.
 *
 * <p><b>OOP concepts:</b> <i>inheritance</i> (extends {@link Person}),
 * <i>encapsulation</i>, <i>constructor overloading</i> and
 * <i>method overriding</i> ({@link #getProfileType()}).</p>
 */
public class Student extends Person {

    private String usn;
    private Gender gender;
    private LocalDate dateOfBirth;
    private int semester;
    private String section;
    private String address;
    private int admissionYear;
    private StudentStatus status;
    private Long userId;

    /** Faculty member assigned as the student's class teacher (nullable). */
    private Long classTeacherId;
    private String classTeacherName;

    /** Faculty member assigned as the student's mentor (nullable). */
    private Long mentorId;
    private String mentorName;

    /** No-argument constructor (Jackson / JDBC row mapping). */
    public Student() {
        super();
        this.status = StudentStatus.ACTIVE;
    }

    /** Partial constructor - only identity fields. */
    public Student(Long id, String usn, String name) {
        super(id, name, null, null, null);
        this.usn = usn;
        this.status = StudentStatus.ACTIVE;
    }

    /** Full constructor - used when building a record from an incoming DTO. */
    public Student(Long id, String usn, String name, String email, String phone,
                   Gender gender, LocalDate dateOfBirth, String department, int semester,
                   String section, String address, int admissionYear,
                   StudentStatus status, Long userId) {
        super(id, name, email, phone, department);
        this.usn = usn;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.semester = semester;
        this.section = section;
        this.address = address;
        this.admissionYear = admissionYear;
        this.status = status;
        this.userId = userId;
    }

    /** Polymorphic answer to {@link Person#getProfileType()}. */
    @Override
    public String getProfileType() {
        return "Student";
    }

    @Override
    public String getDisplayName() {
        return getUsn() + " - " + getName();
    }

    /** Derived value computed from the date of birth. */
    public int getAge() {
        return dateOfBirth == null ? 0 : Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    /** {@code true} when the student record is currently active. */
    public boolean isActive() {
        return status == StudentStatus.ACTIVE;
    }

    public String getUsn() {
        return usn;
    }

    public void setUsn(String usn) {
        this.usn = usn;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) {
        this.semester = semester;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getAdmissionYear() {
        return admissionYear;
    }

    public void setAdmissionYear(int admissionYear) {
        this.admissionYear = admissionYear;
    }

    public StudentStatus getStatus() {
        return status;
    }

    public void setStatus(StudentStatus status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getClassTeacherId() {
        return classTeacherId;
    }

    public void setClassTeacherId(Long classTeacherId) {
        this.classTeacherId = classTeacherId;
    }

    public String getClassTeacherName() {
        return classTeacherName;
    }

    public void setClassTeacherName(String classTeacherName) {
        this.classTeacherName = classTeacherName;
    }

    public Long getMentorId() {
        return mentorId;
    }

    public void setMentorId(Long mentorId) {
        this.mentorId = mentorId;
    }

    public String getMentorName() {
        return mentorName;
    }

    public void setMentorName(String mentorName) {
        this.mentorName = mentorName;
    }

    @Override
    public String toString() {
        return "Student{" + usn + ", " + getName() + ", sem=" + semester + "}";
    }
}
