package com.smartcampus.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.smartcampus.model.enums.Role;

/**
 * Central definition of what each role may do.
 *
 * <p>Permission keys follow the pattern {@code <module>.<action>} and are sent
 * to the browser inside the login response.  The front end uses them only to
 * hide irrelevant menu items and buttons - <b>every key is re-checked on the
 * server</b> by {@code @RequireRoles}, so a tampered browser gains nothing.</p>
 */
public final class Permissions {

    public static final String DASHBOARD_READ = "dashboard.read";

    public static final String STUDENTS_READ = "students.read";
    public static final String STUDENTS_WRITE = "students.write";

    public static final String FACULTY_READ = "faculty.read";
    public static final String FACULTY_WRITE = "faculty.write";

    public static final String COURSES_READ = "courses.read";
    public static final String COURSES_WRITE = "courses.write";

    public static final String CLASSROOMS_READ = "classrooms.read";
    public static final String CLASSROOMS_WRITE = "classrooms.write";

    public static final String LABORATORIES_READ = "laboratories.read";
    public static final String LABORATORIES_WRITE = "laboratories.write";

    public static final String EQUIPMENT_READ = "equipment.read";
    public static final String EQUIPMENT_WRITE = "equipment.write";

    public static final String ACTIVITIES_READ = "activities.read";
    public static final String ACTIVITIES_WRITE = "activities.write";

    public static final String BOOKINGS_READ = "bookings.read";
    public static final String BOOKINGS_READ_ALL = "bookings.readAll";
    public static final String BOOKINGS_WRITE = "bookings.write";
    public static final String BOOKINGS_APPROVE = "bookings.approve";

    public static final String REPORTS_READ = "reports.read";

    public static final String USERS_READ = "users.read";
    public static final String USERS_WRITE = "users.write";

    public static final String SETTINGS_READ = "settings.read";
    public static final String SETTINGS_WRITE = "settings.write";

    private static final List<String> ALL = List.of(
            DASHBOARD_READ,
            STUDENTS_READ, STUDENTS_WRITE,
            FACULTY_READ, FACULTY_WRITE,
            COURSES_READ, COURSES_WRITE,
            CLASSROOMS_READ, CLASSROOMS_WRITE,
            LABORATORIES_READ, LABORATORIES_WRITE,
            EQUIPMENT_READ, EQUIPMENT_WRITE,
            ACTIVITIES_READ, ACTIVITIES_WRITE,
            BOOKINGS_READ, BOOKINGS_READ_ALL, BOOKINGS_WRITE, BOOKINGS_APPROVE,
            REPORTS_READ,
            USERS_READ, USERS_WRITE,
            SETTINGS_READ, SETTINGS_WRITE);

    private Permissions() {
        // utility class - not instantiable
    }

    /** Ordered permission set of a role (also drives the sidebar visibility). */
    public static Set<String> forRole(Role role) {
        Set<String> granted = new LinkedHashSet<>();

        // every signed-in user may look at the dashboard and their own settings
        granted.add(DASHBOARD_READ);
        granted.add(SETTINGS_READ);

        if (role == null) {
            return granted;
        }

        switch (role) {
            case ADMIN -> granted.addAll(ALL);

            case FACULTY -> {
                // read-mostly academic access + full booking/activity management
                granted.add(STUDENTS_READ);
                granted.add(FACULTY_READ);
                granted.add(COURSES_READ);
                granted.add(COURSES_WRITE);
                granted.add(CLASSROOMS_READ);
                granted.add(LABORATORIES_READ);
                granted.add(EQUIPMENT_READ);
                granted.add(ACTIVITIES_READ);
                granted.add(ACTIVITIES_WRITE);
                granted.add(BOOKINGS_READ);
                granted.add(BOOKINGS_READ_ALL);
                granted.add(BOOKINGS_WRITE);
                granted.add(BOOKINGS_APPROVE);
                granted.add(REPORTS_READ);
            }

            case STUDENT -> {
                // personal, read-only view plus the ability to request bookings
                granted.add(STUDENTS_READ);
                granted.add(COURSES_READ);
                granted.add(CLASSROOMS_READ);
                granted.add(LABORATORIES_READ);
                granted.add(EQUIPMENT_READ);
                granted.add(ACTIVITIES_READ);
                granted.add(BOOKINGS_READ);
                granted.add(BOOKINGS_WRITE);
                granted.add(SETTINGS_WRITE);   // students may change their own password
            }
        }
        return granted;
    }

    /** Convenience predicate used by controllers and the front end alike. */
    public static boolean roleHas(Role role, String permission) {
        return forRole(role).contains(permission);
    }
}
