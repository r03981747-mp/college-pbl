package com.smartcampus.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.smartcampus.model.CampusActivity;

/**
 * Every number shown on the dashboard, computed live from MySQL.
 * Nothing here is hardcoded - see {@code DashboardRepository} for the SQL.
 */
public class DashboardStatistics {

    /** Roll-up of one resource module (classrooms / laboratories / equipment). */
    public static class ResourceSummary {
        private long total;
        private long active;
        private long maintenance;
        private long inactive;

        public ResourceSummary() {
        }

        public ResourceSummary(long total, long active, long maintenance, long inactive) {
            this.total = total;
            this.active = active;
            this.maintenance = maintenance;
            this.inactive = inactive;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public long getActive() {
            return active;
        }

        public void setActive(long active) {
            this.active = active;
        }

        public long getMaintenance() {
            return maintenance;
        }

        public void setMaintenance(long maintenance) {
            this.maintenance = maintenance;
        }

        public long getInactive() {
            return inactive;
        }

        public void setInactive(long inactive) {
            this.inactive = inactive;
        }
    }

    // ---- headline cards -------------------------------------------------
    private long totalStudents;
    private long totalFaculty;
    private long totalCourses;
    private long totalClassrooms;
    private long totalLaboratories;
    private long totalEquipment;
    private long upcomingActivities;
    private long activeBookings;

    // ---- charts ---------------------------------------------------------
    private Map<String, Long> studentsByDepartment = new LinkedHashMap<>();
    private Map<String, Long> studentsBySemester = new LinkedHashMap<>();
    private Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
    private Map<String, ResourceSummary> resourceUtilization = new LinkedHashMap<>();
    private List<CampusActivity> upcomingEvents = new ArrayList<>();
    private List<Map<String, Object>> recentBookings = new ArrayList<>();

    public long getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(long totalStudents) {
        this.totalStudents = totalStudents;
    }

    public long getTotalFaculty() {
        return totalFaculty;
    }

    public void setTotalFaculty(long totalFaculty) {
        this.totalFaculty = totalFaculty;
    }

    public long getTotalCourses() {
        return totalCourses;
    }

    public void setTotalCourses(long totalCourses) {
        this.totalCourses = totalCourses;
    }

    public long getTotalClassrooms() {
        return totalClassrooms;
    }

    public void setTotalClassrooms(long totalClassrooms) {
        this.totalClassrooms = totalClassrooms;
    }

    public long getTotalLaboratories() {
        return totalLaboratories;
    }

    public void setTotalLaboratories(long totalLaboratories) {
        this.totalLaboratories = totalLaboratories;
    }

    public long getTotalEquipment() {
        return totalEquipment;
    }

    public void setTotalEquipment(long totalEquipment) {
        this.totalEquipment = totalEquipment;
    }

    public long getUpcomingActivities() {
        return upcomingActivities;
    }

    public void setUpcomingActivities(long upcomingActivities) {
        this.upcomingActivities = upcomingActivities;
    }

    public long getActiveBookings() {
        return activeBookings;
    }

    public void setActiveBookings(long activeBookings) {
        this.activeBookings = activeBookings;
    }

    public Map<String, Long> getStudentsByDepartment() {
        return studentsByDepartment;
    }

    public void setStudentsByDepartment(Map<String, Long> studentsByDepartment) {
        this.studentsByDepartment = studentsByDepartment;
    }

    public Map<String, Long> getStudentsBySemester() {
        return studentsBySemester;
    }

    public void setStudentsBySemester(Map<String, Long> studentsBySemester) {
        this.studentsBySemester = studentsBySemester;
    }

    public Map<String, Long> getBookingsByStatus() {
        return bookingsByStatus;
    }

    public void setBookingsByStatus(Map<String, Long> bookingsByStatus) {
        this.bookingsByStatus = bookingsByStatus;
    }

    public Map<String, ResourceSummary> getResourceUtilization() {
        return resourceUtilization;
    }

    public void setResourceUtilization(Map<String, ResourceSummary> resourceUtilization) {
        this.resourceUtilization = resourceUtilization;
    }

    public List<CampusActivity> getUpcomingEvents() {
        return upcomingEvents;
    }

    public void setUpcomingEvents(List<CampusActivity> upcomingEvents) {
        this.upcomingEvents = upcomingEvents;
    }

    public List<Map<String, Object>> getRecentBookings() {
        return recentBookings;
    }

    public void setRecentBookings(List<Map<String, Object>> recentBookings) {
        this.recentBookings = recentBookings;
    }
}
