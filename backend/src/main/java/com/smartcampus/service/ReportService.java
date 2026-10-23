package com.smartcampus.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.SmartCampusException;
import com.smartcampus.model.Booking;
import com.smartcampus.model.CampusActivity;
import com.smartcampus.model.Classroom;
import com.smartcampus.model.Course;
import com.smartcampus.model.Equipment;
import com.smartcampus.model.Faculty;
import com.smartcampus.model.Laboratory;
import com.smartcampus.model.Resource;
import com.smartcampus.model.Student;
import com.smartcampus.repository.ActivityRepository;
import com.smartcampus.repository.BookingRepository;
import com.smartcampus.repository.ClassroomRepository;
import com.smartcampus.repository.CourseRepository;
import com.smartcampus.repository.EquipmentRepository;
import com.smartcampus.repository.FacultyRepository;
import com.smartcampus.repository.LaboratoryRepository;
import com.smartcampus.repository.StudentRepository;

/**
 * Generates CSV reports using <b>Java file handling</b>.
 *
 * <p>Every export:</p>
 * <ol>
 *   <li>queries MySQL,</li>
 *   <li>writes a real file under {@code reports/} with
 *       {@link BufferedWriter} (create / append options),</li>
 *   <li>reads it back and returns the bytes so the browser can download
 *       {@code students.csv}, {@code bookings.csv}, ...</li>
 * </ol>
 *
 * <p><b>Syllabus concepts:</b> <i>file handling</i> (BufferedWriter, Files,
 * Path), <i>method overloading</i> ({@code export(type)} and
 * {@code export(type, criteria)}), <i>generics</i>, <i>lambdas</i>
 * ({@code Function}) and the <i>Stream API</i>.</p>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** The generated file: name, media type and the CSV payload. */
    public record GeneratedReport(String fileName, String contentType, String content,
                                  int rowCount) {
    }

    private final StudentRepository studentRepository;
    private final FacultyRepository facultyRepository;
    private final CourseRepository courseRepository;
    private final ClassroomRepository classroomRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final EquipmentRepository equipmentRepository;
    private final ActivityRepository activityRepository;
    private final BookingRepository bookingRepository;

    @Value("${app.export.directory:reports}")
    private String exportDirectory;

    public ReportService(StudentRepository studentRepository,
                         FacultyRepository facultyRepository,
                         CourseRepository courseRepository,
                         ClassroomRepository classroomRepository,
                         LaboratoryRepository laboratoryRepository,
                         EquipmentRepository equipmentRepository,
                         ActivityRepository activityRepository,
                         BookingRepository bookingRepository) {
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.courseRepository = courseRepository;
        this.classroomRepository = classroomRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.equipmentRepository = equipmentRepository;
        this.activityRepository = activityRepository;
        this.bookingRepository = bookingRepository;
    }

    // ==================================================================
    //  Overloaded public API
    // ==================================================================

    /** Exports every record of the requested report (no filters). */
    public GeneratedReport export(String reportType) {
        return export(reportType, new SearchCriteria());
    }

    /**
     * Overloaded export - honours the keyword and filters of the reports page
     * so only the rows currently on screen are written to the file.
     */
    public GeneratedReport export(String reportType, SearchCriteria criteria) {
        if (criteria == null) {
            criteria = new SearchCriteria();
        }
        criteria.normalise();

        String type = reportType == null ? "" : reportType.trim().toLowerCase();

        return switch (type) {
            case "students" -> build("students",
                    List.of("ID", "USN", "Name", "Email", "Phone", "Gender", "DOB",
                            "Department", "Semester", "Section", "Admission Year", "Status"),
                    studentRepository.search(criteria),
                    studentRow());

            case "faculty" -> build("faculty",
                    List.of("ID", "Employee ID", "Name", "Email", "Phone", "Department",
                            "Designation", "Qualification", "Joining Date", "Status"),
                    facultyRepository.search(criteria),
                    facultyRow());

            case "courses" -> build("courses",
                    List.of("ID", "Course Code", "Course Name", "Department", "Semester",
                            "Credits", "Assigned Faculty", "Status", "Description"),
                    courseRepository.search(criteria),
                    courseRow());

            case "classrooms" -> build("classrooms",
                    List.of("ID", "Room Number", "Building", "Floor", "Capacity",
                            "Type", "Facilities", "Status"),
                    classroomRepository.search(criteria),
                    classroomRow());

            case "laboratories" -> build("laboratories",
                    List.of("ID", "Lab Number", "Lab Name", "Building", "Department",
                            "Capacity", "Lab Type", "Equipment Count", "Status"),
                    laboratoryRepository.search(criteria),
                    laboratoryRow());

            case "equipment" -> build("equipment",
                    List.of("ID", "Equipment Name", "Category", "Serial Number", "Quantity",
                            "Location", "Purchase Date", "Condition", "Status"),
                    equipmentRepository.search(criteria),
                    equipmentRow());

            // one report that mixes classrooms, laboratories and equipment:
            // every element is a Resource, and the correct overridden
            // getResourceLabel() is chosen at RUN TIME (polymorphism)
            case "resources" -> build("resources",
                    List.of("Kind", "Code", "Label", "Status"),
                    allResources(),
                    resourceRow());

            case "bookings" -> build("bookings",
                    List.of("ID", "Booking Ref", "Resource Type", "Resource",
                            "Requested By", "Date", "Start", "End", "Purpose", "Status"),
                    bookingRepository.search(criteria),
                    bookingRow());

            case "activities" -> build("activities",
                    List.of("ID", "Activity", "Category", "Organizer", "Venue",
                            "Start Date", "End Date", "Start Time", "End Time",
                            "Max Participants", "Status"),
                    activityRepository.search(criteria),
                    activityRow());

            default -> throw new SmartCampusException(
                    "Unknown report type '" + reportType + "'. Valid types are: students, "
                            + "faculty, courses, classrooms, laboratories, equipment, "
                            + "resources, bookings, activities.");
        };
    }

    /** Report types supported by this service (shown as buttons on the page). */
    public List<String> supportedTypes() {
        return List.of("students", "faculty", "courses", "classrooms", "laboratories",
                "equipment", "resources", "bookings", "activities");
    }

    // ==================================================================
    //  Row builders - each is a lambda passed to build(...)
    // ==================================================================

    private static Function<Student, List<String>> studentRow() {
        return student -> List.of(
                text(student.getId()), student.getUsn(), student.getName(), student.getEmail(),
                student.getPhone(),
                student.getGender() == null ? "" : student.getGender().getLabel(),
                text(student.getDateOfBirth()), text(student.getDepartment()),
                text(student.getSemester()), text(student.getSection()),
                text(student.getAdmissionYear()),
                student.getStatus() == null ? "" : student.getStatus().getLabel());
    }

    private static Function<Faculty, List<String>> facultyRow() {
        return faculty -> List.of(
                text(faculty.getId()), faculty.getEmployeeId(), faculty.getName(),
                faculty.getEmail(), faculty.getPhone(), text(faculty.getDepartment()),
                text(faculty.getDesignation()), text(faculty.getQualification()),
                text(faculty.getJoiningDate()),
                faculty.getStatus() == null ? "" : faculty.getStatus().getLabel());
    }

    private static Function<Course, List<String>> courseRow() {
        return course -> List.of(
                text(course.getId()), course.getCourseCode(), course.getCourseName(),
                text(course.getDepartment()), text(course.getSemester()),
                text(course.getCredits()), text(course.getFacultyName()),
                course.getStatus() == null ? "" : course.getStatus().getLabel(),
                text(course.getDescription()));
    }

    /** Polymorphic row: works for classrooms, laboratories and equipment alike. */
    private static Function<Resource, List<String>> resourceRow() {
        return resource -> List.of(
                resource.getClass().getSimpleName(),
                resource.getResourceCode(),
                resource.getResourceLabel(),
                resource.getStatus() == null ? "" : resource.getStatus().getLabel());
    }

    private static Function<Classroom, List<String>> classroomRow() {
        return room -> List.of(
                text(room.getId()), text(room.getRoomNumber()), text(room.getBuilding()),
                text(room.getFloor()), text(room.getCapacity()),
                room.getType() == null ? "" : room.getType().getLabel(),
                text(room.getFacilities()),
                room.getStatus() == null ? "" : room.getStatus().getLabel());
    }

    private static Function<Laboratory, List<String>> laboratoryRow() {
        return lab -> List.of(
                text(lab.getId()), text(lab.getLabNumber()), text(lab.getLabName()),
                text(lab.getBuilding()), text(lab.getDepartment()), text(lab.getCapacity()),
                text(lab.getLabType()), text(lab.getEquipmentCount()),
                lab.getStatus() == null ? "" : lab.getStatus().getLabel());
    }

    private static Function<Equipment, List<String>> equipmentRow() {
        return item -> List.of(
                text(item.getId()), text(item.getEquipmentName()), text(item.getCategory()),
                text(item.getSerialNumber()), text(item.getQuantity()), text(item.getLocation()),
                text(item.getPurchaseDate()),
                item.getCondition() == null ? "" : item.getCondition().getLabel(),
                item.getStatus() == null ? "" : item.getStatus().getLabel());
    }

    private static Function<Booking, List<String>> bookingRow() {
        return booking -> List.of(
                text(booking.getId()), booking.getBookingRef(),
                booking.getResourceType().getLabel(), text(booking.getResourceName()),
                text(booking.getRequestedByName()), text(booking.getBookingDate()),
                text(booking.getStartTime()), text(booking.getEndTime()),
                text(booking.getPurpose()),
                booking.getStatus() == null ? "" : booking.getStatus().getLabel());
    }

    private static Function<CampusActivity, List<String>> activityRow() {
        return activity -> List.of(
                text(activity.getId()), activity.getActivityName(),
                activity.getCategory() == null ? "" : activity.getCategory().getLabel(),
                text(activity.getOrganizer()), text(activity.getVenue()),
                text(activity.getStartDate()), text(activity.getEndDate()),
                text(activity.getStartTime()), text(activity.getEndTime()),
                text(activity.getMaxParticipants()),
                activity.getStatus() == null ? "" : activity.getStatus().getLabel());
    }

    /** Classrooms + laboratories + equipment in a single polymorphic list. */
    private List<Resource> allResources() {
        List<Resource> resources = new ArrayList<>();
        resources.addAll(classroomRepository.findAll());
        resources.addAll(laboratoryRepository.findAll());
        resources.addAll(equipmentRepository.findAll());
        return resources;
    }

    // ==================================================================
    //  CSV + FILE HANDLING
    // ==================================================================

    private <T> GeneratedReport build(String type, List<String> headers,
                                      List<T> rows, Function<T, List<String>> mapper) {
        List<List<String>> mapped = rows.stream().map(mapper).collect(java.util.stream.Collectors.toList());

        String fileName = type + "-" + LocalDate.now() + ".csv";
        Path file = writeCsv(fileName, headers, mapped);
        String content = readFile(file);

        log.info("Generated report {} ({} data rows) at {}",
                fileName, mapped.size(), file.toAbsolutePath());
        return new GeneratedReport(fileName, "text/csv;charset=UTF-8", content, mapped.size());
    }

    /**
     * Writes the CSV file to disk.
     *
     * <p>Creates {@code reports/} when it does not exist and always
     * truncates an older file with the same name.</p>
     */
    private Path writeCsv(String fileName, List<String> headers, List<List<String>> rows) {
        Path directory = Paths.get(exportDirectory);
        Path file = directory.resolve(fileName);

        try {
            Files.createDirectories(directory);

            try (BufferedWriter writer = Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {

                writer.write(escapeAll(headers));
                writer.newLine();
                for (List<String> row : rows) {
                    writer.write(escapeAll(row));
                    writer.newLine();
                }
            }
            return file;
        } catch (IOException ex) {
            throw new SmartCampusException(
                    "Could not write the report file. Check that the application "
                            + "has permission to create the '" + exportDirectory + "' folder.", ex);
        }
    }

    /** Reads the generated file back - the other half of the file handling demo. */
    private String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new SmartCampusException("Could not read the generated report file.", ex);
        }
    }

    /** Escapes one line of CSV according to RFC 4180. */
    private static String escapeAll(List<String> values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(values.get(i)));
        }
        return line.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains(";")) {
            return '"' + cleaned.replace("\"", "\"\"") + '"';
        }
        return cleaned;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
