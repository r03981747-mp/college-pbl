# Smart Campus — Integrated Management System

## Project Documentation

> A complete full-stack Object-Oriented Programming project:
> **HTML5 / CSS3 / JavaScript + Bootstrap 5** front end,
> **Spring Boot 3 + Java 25 + hand-written JDBC** back end,
> **MySQL 8** database, with a separate **security layer**.

---

## Table of contents

1. [System overview](#1-system-overview)
2. [Architecture](#2-architecture)
3. [Database design](#3-database-design)
4. [OOP concept mapping](#4-oop-concept-mapping)
5. [REST API reference](#5-rest-api-reference)
6. [Security layer](#6-security-layer)
7. [Concurrency & double-booking control](#7-concurrency--double-booking-control)
8. [File handling](#8-file-handling)
9. [Front end design](#9-front-end-design)
10. [Testing](#10-testing)
11. [How to run the project](#11-how-to-run-the-project)
12. [Limitations and future enhancements](#12-limitations-and-future-enhancements)

---

## 1. System overview

Smart Campus is a single portal through which a college manages every shared
resource. Three roles use it:

| Role | Can do |
|---|---|
| **ADMIN** | Everything: users, all CRUD, approvals, reports, concurrency test |
| **FACULTY** | Read students/faculty, manage courses & activities, approve bookings, reports |
| **STUDENT** | Read courses/resources/activities, create & cancel own bookings, change own password |

Everything displayed is computed from the database at request time.
There is **no fake data, no placeholder button and no hardcoded dashboard number**.

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│  BROWSER  (frontend/)                                            │
│  HTML5 · CSS3 · Bootstrap 5 · Chart.js · Fetch API               │
│  api.js → token in localStorage · auth.js → role UI gating       │
└───────────────────────────┬──────────────────────────────────────┘
                            │  HTTPS-shaped JSON over fetch
┌───────────────────────────▼──────────────────────────────────────┐
│  SPRING BOOT  (backend/)                                    │
│                                                                  │
│  Controller layer   13 @RestController classes                   │
│       ↓                                                          │
│  Security layer     AuthInterceptor + @RequireRoles  (401 / 403) │
│       ↓                                                          │
│  Service layer      business rules, validation, locks, audit     │
│       ↓                                                          │
│  Repository layer   GenericRepository<T,ID> + JdbcSupport        │
│       ↓  java.sql.PreparedStatement (values always bound)        │
└───────────────┬──────────────────────────────────────────────────┘
                │
┌───────────────▼──────────────────────────────────────────────────┐
│  MySQL 8  ·  smart_campus_db  ·  11 tables                       │
└──────────────────────────────────────────────────────────────────┘
```

### Layer responsibilities

| Layer | Package | Responsibility |
|---|---|---|
| Controller | `com.smartcampus.controller` | HTTP verbs, paths, status codes, `@RequireRoles` |
| Security | `com.smartcampus.security` | Sessions, BCrypt, token resolution, permissions |
| Service | `com.smartcampus.service` | Validation, business rules, locking, audit |
| Repository | `com.smartcampus.repository` | SQL, `PreparedStatement`, row mapping |
| Model | `com.smartcampus.model` | Entities + `enums` |
| DTO | `com.smartcampus.dto` | Request/response shapes, paging |
| Exception | `com.smartcampus.exception` | Custom exceptions + one global handler |
| Utility | `com.smartcampus.utility` | `SqlCriteria`, `TransactionExecutor`, `Validators` |

**Why hand-written JDBC?** The brief explicitly requires demonstrable JDBC with
`PreparedStatement` CRUD. Using JPA/Hibernate would hide exactly that, so the
project deliberately uses plain `java.sql` inside a small abstract base class
(`JdbcSupport`) that every repository inherits from.

---

## 3. Database design

Database: `smart_campus_db` — created by `database/schema.sql`,
seeded by `database/sample_data.sql`.

| # | Table | Purpose | Seed rows |
|---|---|---|---|
| 1 | `students` | Student register (USN is the natural key) | 12 |
| 2 | `faculty` | Teaching staff (employee code) | 6 |
| 3 | `courses` | Courses + optional `faculty_id` assignment | 10 |
| 4 | `classrooms` | Rooms (`Resource` subclass) | 7 |
| 5 | `laboratories` | Labs (`Resource` subclass) | 5 |
| 6 | `equipment` | Inventory items (`Resource` subclass) | 12 |
| 7 | `activities` | Campus events | 8 |
| 8 | `bookings` | Reservations of classroom/lab/equipment | 10 |
| 9 | `users` | Login accounts + role + BCrypt hash | 7 |
| 10 | `user_sessions` | DB-backed session tokens (survive restarts) | — |
| 11 | `activity_logs` | Audit trail | — |

Notable design points:

* **Polymorphic association.** `bookings.resource_type` + `bookings.resource_id`
  point at `classrooms`, `laboratories` **or** `equipment`. There is deliberately
  no foreign key — the service validates existence and `ACTIVE` status, which is
  itself a demonstration of runtime polymorphism.
* **Reserved word.** MySQL 8 reserves `condition`, so it is always written as
  `` `condition` `` in SQL. Reading it with `rs.getString("condition")` works normally.
* **Integrity.** Unique indexes (USN, employee code, course code, room number,
  login e-mail), CHECK constraints (semester 1-8, capacity > 0, quantity ≥ 0,
  time ranges) and FKs on every reference.
* **No plaintext passwords.** `users.password_hash` stores a BCrypt hash
  (`$2a$10$…`, cost 10). The API never serialises that column.

Seed counts verified after import:
`students 12 · faculty 6 · courses 10 · classrooms 7 · laboratories 5 ·
equipment 12 · activities 8 · bookings 10 · users 7`

---

## 4. OOP concept mapping

Every required concept is listed with the **exact file** that demonstrates it.

### 4.1 Core language features

| Concept | Where it is demonstrated | Notes |
|---|---|---|
| **Classes** | `model/Student.java`, `model/Course.java`, `model/Booking.java`, … | 12 entity classes, 13 DTOs, 13 controllers, 13 services, 12 repositories |
| **Objects** | Every controller method creates/uses instances passed through the layers | e.g. `StudentController.list()` builds `PagedResponse<Student>` |
| **Constructors** | `model/Student.java` (`Student()`, `Student(Long,String,String)`, full-args), `dto/LoginRequest.java`, `service/StudentServiceImpl.java` | no-arg + parameterised + copy constructors |
| **Encapsulation** | All model fields are `private` with getters/setters (`Person`, `Student`, `Booking`, `Resource`) | plus `final` fields in `AuthenticatedUser`, `SearchCriteria`, `ApiResponse` |
| **Access modifiers** | `private` fields, `protected` JDBC helpers in `JdbcSupport` (visible to subclasses only), `public` API | `JdbcSupport.query/update/insert` are `protected` |
| **static** | `SqlCriteria.orderBy(...)`, `SearchCriteria.from(...)`, `AuthContext.get/set/clear`, `ApiResponse.ok(...)` | static factory + ThreadLocal holder |
| **`this`** | `BookingServiceImpl` setters, `CrudTable` (JS) | |
| **final** | `private final` service/repository fields, `final` DTO lists | dependency injection by constructor |
| **Immutable value objects** | `dto/AuthenticatedUser` (record-style), `dto/SearchCriteria` | |

### 4.2 Inheritance

| Hierarchy | Parent | Children |
|---|---|---|
| Person → Student / Faculty | `model/Person.java` (**abstract**) | `model/Student.java`, `model/Faculty.java` |
| Resource → Classroom / Laboratory / Equipment | `model/Resource.java` (**abstract**) | `model/Classroom.java`, `model/Laboratory.java`, `model/Equipment.java` |
| Entity base | `model/BaseEntity.java` (**abstract**) | `UserAccount`, `SessionToken`, `Booking`, `Course`, `CampusActivity`, `ActivityLog`, `Person`, `Resource` |
| Exception base | `exception/SmartCampusException.java` | 13 subclasses (see §4.6) |
| JDBC base | `repository/JdbcSupport.java` (**abstract**) | all 12 repositories |

`Person` and `Resource` inherit from `BaseEntity`, giving a **three-level chain**:
`BaseEntity → Person → Student`.

### 4.3 Abstraction — abstract classes & interfaces

**Abstract classes**

| Class | Abstract members |
|---|---|
| `model/BaseEntity.java` | `getId()` / `setId(Long)` contract |
| `model/Person.java` | `public abstract String getProfileType();` |
| `model/Resource.java` | `public abstract String getResourceCode();`<br>`public abstract String getResourceLabel();` |
| `repository/JdbcSupport.java` | cannot be instantiated; subclasses supply SQL |

**Interfaces**

| Interface | File | Implemented by |
|---|---|---|
| `Searchable<T>` | `service/Searchable.java` | `StudentService`, `FacultyService`, `CourseService`, `ActivityService`, `BookingService`, `ResourceService<T>` |
| `Manageable<T>` | `service/Manageable.java` | same set — `create / update / delete / findById` |
| `ResourceService<T extends Resource>` | `service/ResourceService.java` | `ClassroomService`, `LaboratoryService`, `EquipmentService` — itself extends `Searchable<T>` **and** `Manageable<T>` |
| `GenericRepository<T, ID>` | `repository/GenericRepository.java` | every repository |
| `SearchableRepository<T>` | `repository/SearchableRepository.java` | every searchable repository |
| `JdbcSupport.RowMapper<T>` | `repository/JdbcSupport.java` | used through lambdas |
| `TransactionExecutor.SqlWork<R>` | `utility/TransactionExecutor.java` | used through lambdas |

> `ResourceService<T extends Resource>` is a **bounded generic** — it will not
> compile for a type that is not a `Resource`.

### 4.4 Polymorphism

**Compile-time (overloading)**

| File | Overloads |
|---|---|
| `service/ReportService.java` | `export(type)` · `export(type, criteria)` — no-arg vs criteria variant |
| `repository/JdbcSupport.java` | `query(sql, mapper, params…)` · `queryOne(sql, mapper, params…)` · `update(sql, params…)` · `insert(sql, keys, params…)` |
| `utility/Validators.java` | `requiredText(value, field)` · `requiredText(value, field, maxLength)` |
| `exception/SmartCampusException.java` | `SmartCampusException(msg)` · `SmartCampusException(msg, cause)` |
| `model/Student.java` | three constructors of different arity |

**Runtime (overriding)**

| Method | Overridden in |
|---|---|
| `getResourceCode()` / `getResourceLabel()` | `Classroom`, `Laboratory`, `Equipment` |
| `getProfileType()` | `Student` → `"STUDENT"`, `Faculty` → `"FACULTY"` |
| `toString()` | most model classes |
| `search()` / `count()` / `create()` / `update()` / `delete()` | every repository and service implementation |
| every `Searchable` / `Manageable` method | `StudentServiceImpl`, `FacultyServiceImpl`, `CourseServiceImpl`, `ActivityServiceImpl`, `BookingServiceImpl` |

**Where polymorphism is visible at runtime**

1. **Resource rendering** — `BookingServiceImpl` holds a single
   `Resource` reference and calls `isBookable()` / `getResourceLabel()` /
   `getStatus()`; the JVM dispatches to the correct subclass implementation.
2. **Report generation** — `ReportService.export(...)` switches on the report
   type and streams a different repository through one shared CSV writer.
3. **Row mapping** — `JdbcSupport.query(sql, mapper, …)` receives a different
   `RowMapper` lambda per table but runs the identical loop.
4. **Exception handling** — `GlobalExceptionHandler` catches the abstract
   `SmartCampusException` once and each subclass produces its own status code.

### 4.5 Generics

| File | Generic form |
|---|---|
| `repository/GenericRepository.java` | `interface GenericRepository<T, ID>` — one implementation serves 8 entities |
| `repository/SearchableRepository.java` | `interface SearchableRepository<T>` |
| `service/ResourceService.java` | `ResourceService<T extends Resource>` (bounded) |
| `service/Searchable.java`, `service/Manageable.java` | `Searchable<T>`, `Manageable<T>` |
| `repository/JdbcSupport.java` | `RowMapper<T>`, `<T> List<T> query(...)`, `<T> Optional<T> queryOne(...)` |
| `utility/TransactionExecutor.java` | `doInTransaction(SqlWork<R>)` → returns `R` |
| `dto/ApiResponse<T>`, `dto/PagedResponse<T>` | envelope over any payload |
| `dto/SearchCriteria.java` | `List<Object> params` bound generically |

**Demonstrable line of code:**

```java
public interface GenericRepository<T, ID> {
    Optional<T> findById(ID id);
    List<T> findAll();
    T save(T entity);
    T update(T entity);
    boolean deleteById(ID id);
}
```

and its use:

```java
public class StudentRepository extends JdbcSupport
        implements GenericRepository<Student, Long>, SearchableRepository<Student> { … }
```

*(The repositories return `Optional<T>` from `findById` instead of a bare `T`.
This is a documented deviation from the sample signature — it makes
`orElseThrow(...)` explicit and prevents accidental `null` handling.)*

### 4.6 Custom exceptions

| Exception | HTTP | Thrown when |
|---|---|---|
| `SmartCampusException` | 500 | base class, generic DB failure |
| `ResourceNotFoundException` | 404 | generic "record not found" |
| `StudentNotFoundException` | 404 | unknown student id/USN |
| `FacultyNotFoundException` | 404 | unknown faculty id |
| `CourseNotFoundException` | 404 | unknown course id |
| `ActivityNotFoundException` | 404 | unknown activity id |
| `UserNotFoundException` | 404 | unknown user id |
| `DuplicateRecordException` | 400/409 | unique key already taken (USN, e-mail…) |
| `InvalidInputException` | 400 | field-level validation failure |
| `InvalidBookingException` | 400 | booking payload violates a rule |
| `BookingConflictException` | **409** | time slot already reserved |
| `UnauthorizedException` | 401 | no/expired session |
| `ForbiddenException` | 403 | role lacks permission |

All 13 extend `SmartCampusException` (which extends `RuntimeException`), so the
single `@ExceptionHandler(SmartCampusException.class)` in
`exception/GlobalExceptionHandler.java` is the fallback, while the specific
handlers override it with the right status code. **The browser never sees a
stack trace** — detailed messages go to the server log only.

### 4.7 Collections

| Collection | File / use |
|---|---|
| `List<T>` | every query result; `DashboardStatistics` lists |
| `ArrayList<T>` | `JdbcSupport.query` accumulator; `SearchCriteria.params` |
| `Map<K,V>` | `SORT_COLUMNS` whitelist maps; `DashboardStatistics` group-by maps; `ApiResponse.errors` |
| `Map.of(...)` | immutable whitelist of legal `ORDER BY` columns |
| `LinkedHashMap` | preserves insertion order of audit-log groups |
| `Set<T>` / `LinkedHashSet` | `Permissions.forRole(...)` permission keys |
| `Optional<T>` | `GenericRepository.findById` |
| `EnumMap` / enums | status handling |
| `Collections.unmodifiableList(...)` | DTO defensive copies |
| `Comparator` | sorting helpers in `DashboardService` |
| `Queue` / `Stack` | `Deque` used for CSV row staging |
| `ConcurrentHashMap` | booking lock stripes (see §7) |
| `List<String> outcomes` | concurrency test result rows, guarded by `synchronized` |

### 4.8 Lambdas & functional interfaces

| Lambda | File |
|---|---|
| `(rs, rowNum) -> new Student(...)` | `repository/StudentRepository.STUDENT_ROW` — `RowMapper<Student>` |
| `(rs, rowNum) -> new Booking(...)` | `repository/BookingRepository` |
| `(resultSet, index) -> …` | every repository's row mapper |
| `SqlWork<R>` implementation | `utility/TransactionExecutor` used by `BookingServiceImpl` |
| `resource -> resource.isBookable()` | `.filter(...)` in `BookingServiceImpl` / `DashboardService` |
| `Comparator.comparing(...)` | sort helpers |
| Method references `this::validate` | `BookingServiceImpl` |
| Front-end lambdas | `CrudTable` `columns[].render`, `filters`, `onView/onEdit/onDelete` callbacks in `frontend/js/common.js` |

### 4.9 Streams API

| Stream pipeline | File |
|---|---|
| `.stream().filter().map().collect(toList())` | `DashboardService.recentBookingRows()`, `ReportService` |
| `.stream().collect(groupingBy(...))` | `DashboardService` — bookings by status, students by department |
| `.stream().map(...).collect(joining(...))` | `ReportService` CSV column join |
| `.stream().sorted(comparing(...))` | `DashboardService` upcoming events |
| `.stream().count()` / `.sum()` | `DashboardService`, `ReportService` |
| `.stream().forEach(...)` | `AuditLogService`, `Permissions` |
| `.stream().anyMatch(...)` | `BookingServiceImpl` overlap detection |
| Front-end equivalent | `Array.map / filter / join / reduce` throughout `frontend/js/*.js` |

### 4.10 File handling

| Operation | File |
|---|---|
| **Write** CSV with `BufferedWriter` + `FileWriter` | `service/ReportService.java` → `backend/exports/*.csv` |
| **Read back** the file for download | `ReportService` re-reads the file and returns its bytes |
| **Write** audit log with `BufferedWriter` (append) | `service/AuditLogService.java` → `backend/logs/activity-YYYY-MM-DD.log` |
| Directory creation `Files.createDirectories` | both services |
| Overloaded export | `export(type)` / `export(type, criteria)` |

Files created at runtime (both are real output of the running system):

```
backend/exports/students-2026-10-03.csv
backend/exports/bookings-2026-10-03.csv
backend/logs/activity-2026-10-03.log
```

### 4.11 JDBC & `PreparedStatement`

`repository/JdbcSupport.java` is the single place the JDBC API appears:

```java
try (Connection connection = dataSource.getConnection();
     PreparedStatement statement = connection.prepareStatement(sql)) {
    bind(statement, params);                 // every ? is bound — never concatenated
    try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) results.add(mapper.mapRow(resultSet, ++row));
    }
}
```

* `DataSource` (HikariCP) → `Connection` → `PreparedStatement` → `ResultSet`
* **SQL injection**: user input is *always* bound. The only values that cannot
  be bound are identifiers (`ORDER BY column`), which go through a whitelist
  map in `utility/SqlCriteria.java` — a request such as
  `?sort=DROP TABLE` is silently mapped to the default column.
* CRUD is spread over `insert / update / query / queryOne / queryForLong / exists`.

### 4.12 Annotations

| Annotation | File | Purpose |
|---|---|---|
| **`@RequireRoles`** (custom, RUNTIME retention) | `security/RequireRoles.java` | declares allowed roles on a controller method/class |
| `@RestController`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` | all controllers | REST mapping |
| `@RequestMapping("/api/…")` | all controllers | base path |
| `@RequestBody`, `@PathVariable`, `@RequestParam` | all controllers | binding |
| `@Valid` + `@NotBlank` / `@Size` / `@Email` | DTOs | declarative validation |
| `@ExceptionHandler` + `@RestControllerAdvice` | `GlobalExceptionHandler` | one place for every error |
| `@Override` | every implementation | overriding discipline |
| `@Component`, `@Service`, `@Repository`, `@Configuration` | services, repositories, `WebConfig` | Spring stereotypes |
| `@Autowired` (constructor injection) | all services/controllers | DI |
| `@CrossOrigin` (credentials + allowed origins) | `WebConfig` | CORS for Live Server |
| `@Primary`, `@Qualifier` | `WebConfig` | named `DataSource` / `FrontendLocator` bean |
| `@Transactional` (manual `TransactionExecutor`) | `BookingServiceImpl` | explicit transaction control |

**Custom annotation + interceptor flow:**

```
@RequireRoles({ADMIN, FACULTY})
        ↓  reflected by AuthInterceptor (MethodParameter lookup)
AuthInterceptor.preHandle()
        ↓  token → SessionRepository → users.role  (server-side, never the browser)
   role mismatch → ForbiddenException → 403 JSON
```

### 4.13 Multithreading & synchronisation

| Mechanism | File | Why |
|---|---|---|
| `CountDownLatch` (start line) | `service/ConcurrencyDemoService.java` | all N threads begin at the same instant |
| `CountDownLatch` (finish line) | same | wait for every thread before reading results |
| `new Thread(...)` + `Runnable` | same | N real OS threads, not virtual |
| `synchronized (outcomes)` | same | protect the shared result list |
| `AtomicInteger succeeded/failed` | same | race-free counters |
| `Thread.currentThread().interrupt()` | same | correct interruption handling |
| `AuthContext.set(...) / clear()` | same | `ThreadLocal` does not inherit — must be copied into each worker |
| **`ReentrantLock` striping** | `service/BookingServiceImpl.java` | the actual critical section that prevents double booking |
| `ConcurrentHashMap<String, ReentrantLock>` | same | one lock per `resourceType#resourceId#date` |
| `synchronized nextBookingRef()` | same | guarantees unique booking references |
| `TransactionExecutor.doInTransaction` | same | SELECT-conflict + INSERT run atomically |
| HikariCP connection pool | `application.properties` | concurrent request handling |
| Executor/Tomcat worker threads | Spring Boot | one thread per HTTP request |

See §7 for the full algorithm and the exact message produced.

### 4.14 Front-end OOP

The brief's OOP spirit is mirrored in JavaScript too:

| Concept | JavaScript |
|---|---|
| Class | `class CrudTable` in `frontend/js/common.js` |
| Constructor | `constructor(config)` with `Object.assign` defaults |
| Encapsulation (module pattern) | `const Auth = (() => { … return { login, logout, can } })()` |
| Encapsulation (module pattern) | `const AppShell = (() => { … })()` |
| Methods / this | `table.refresh()`, `this.query`, `this.cfg` |
| Callbacks ≈ interfaces | `onView / onEdit / onDelete / onCustom` |
| Polymorphism | `columns[].render(value, row)` — one table, different cells |
| Composition | `Dashboard` uses `Chart`, `Auth`, `AppShell`, `CrudTable` |

---

## 5. REST API reference

Base URL: `http://localhost:8080/api`
Every response uses the envelope:

```json
{ "success": true, "message": "...", "data": { }, "errors": null, "timestamp": "..." }
```

Paged lists use:

```json
{ "content": [], "page": 0, "size": 10, "totalElements": 12,
  "totalPages": 2, "hasNext": true, "hasPrevious": false }
```

Shared query parameters on every list endpoint:
`keyword` (or `search`), `sort`, `direction`, `page`, `size`, plus
module-specific filters (`department`, `status`, `semester`, …).

Legend: 🔓 public · 👤 any signed-in user · 🅰 ADMIN · 🅵 ADMIN+FACULTY

### 5.1 Authentication — `/api/auth`

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/auth/login` | 🔓 | Validate credentials → token + user + permissions |
| POST | `/auth/logout` | 👤 | Invalidate the session token |
| GET | `/auth/me` | 👤 | Current user profile + permission keys |
| GET | `/auth/status` | 🔓 | `{authenticated: bool, user?}` |
| POST | `/auth/change-password` | 👤 | Change own password (invalidates sessions) |

Request body for login: `{"email":"…","password":"…","rememberMe":true}`
(`username` and `usernameOrEmail` are accepted aliases).

### 5.2 Students — `/api/students`

| Method | Path | Access |
|---|---|---|
| GET | `/students` | 🅰 🅵 |
| GET | `/students/search` | 🅰 🅵 |
| GET | `/students/options` | 🅰 🅵 |
| GET | `/students/me` | 👤 — a student's **own** row, resolved from the session token (this is what `students.html` renders for the STUDENT role) |
| GET | `/students/{id}` | 👤 (ownership checked in the service) |
| POST | `/students` | 🅰 |
| PUT | `/students/{id}` | 🅰 |
| DELETE | `/students/{id}` | 🅰 |

### 5.3 Faculty — `/api/faculty`

| Method | Path | Access |
|---|---|---|
| GET | `/faculty`, `/faculty/search`, `/faculty/options`, `/faculty/{id}` | 🅰 🅵 |
| POST | `/faculty` | 🅰 |
| PUT | `/faculty/{id}` | 🅰 |
| DELETE | `/faculty/{id}` | 🅰 |

### 5.4 Courses — `/api/courses`

| Method | Path | Access |
|---|---|---|
| GET | `/courses`, `/courses/search`, `/courses/options`, `/courses/{id}` | 👤 |
| GET | `/courses/unassigned` | 🅰 🅵 |
| POST | `/courses` | 🅰 🅵 |
| PUT | `/courses/{id}` | 🅰 🅵 |
| PUT | `/courses/{id}/faculty?facultyId={id}` | 🅰 🅵 (empty value unassigns) |
| DELETE | `/courses/{id}` | 🅰 🅵 |

### 5.5 Classrooms — `/api/classrooms`

| Method | Path | Access |
|---|---|---|
| GET | `/classrooms`, `/classrooms/search`, `/classrooms/options`, `/classrooms/{id}` | 👤 |
| POST | `/classrooms` | 🅰 |
| PUT | `/classrooms/{id}` | 🅰 |
| DELETE | `/classrooms/{id}` | 🅰 |
| POST | `/classrooms/{id}/availability` | 👤 |

### 5.6 Laboratories — `/api/laboratories`

Identical shape to classrooms:
`GET list /search /options /{id}` · `POST` · `PUT /{id}` · `DELETE /{id}`
· `POST /{id}/availability` — writes require 🅰.

### 5.7 Equipment — `/api/equipment`

| Method | Path | Access |
|---|---|---|
| GET | `/equipment`, `/equipment/search`, `/equipment/options`, `/equipment/{id}` | 👤 |
| POST | `/equipment` | 🅰 |
| PUT | `/equipment/{id}` | 🅰 |
| PUT | `/equipment/{id}/condition` | 🅰 |
| PUT | `/equipment/{id}/quantity` | 🅰 |
| DELETE | `/equipment/{id}` | 🅰 |
| POST | `/equipment/{id}/availability` | 👤 |

### 5.8 Activities — `/api/activities`

| Method | Path | Access |
|---|---|---|
| GET | `/activities`, `/activities/upcoming`, `/activities/on-date`, `/activities/search`, `/activities/options`, `/activities/{id}` | 👤 |
| POST | `/activities` | 🅰 🅵 |
| PUT | `/activities/{id}` | 🅰 🅵 |
| DELETE | `/activities/{id}` | 🅰 🅵 |

### 5.9 Bookings — `/api/bookings`

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/bookings` | 👤 | students see only their own rows |
| GET | `/bookings/my` | 👤 | shorthand for "my bookings" |
| GET | `/bookings/resources` | 👤 | `[{type,id,label,status}]` for the picker |
| GET | `/bookings/search` | 👤 | |
| GET | `/bookings/{id}` | 👤 | ownership checked |
| POST | `/bookings` | 👤 | **409 on conflict** |
| POST | `/bookings/availability` | 👤 | dry-run check |
| PUT | `/bookings/{id}/status` | 🅰 🅵 | APPROVE / REJECT / COMPLETE |
| POST | `/bookings/{id}/cancel` | 👤 owner | |
| DELETE | `/bookings/{id}` | 🅰 | |

### 5.10 Dashboard — `/api/dashboard`

| Method | Path | Access |
|---|---|---|
| GET | `/dashboard/statistics` | 👤 |

Returns `DashboardStatistics`: `totalStudents`, `totalFaculty`, `totalCourses`,
`totalClassrooms`, `totalLaboratories`, `totalEquipment`, `activeBookings`,
`pendingBookings`, `approvedBookings`, `upcomingActivities`,
`studentsByDepartment`, `studentsBySemester`, `bookingsByStatus`,
`resourceUtilization`, `equipmentCondition`, `upcomingEvents`,
`recentBookings` — **all produced by aggregate SQL**, none hardcoded.

### 5.11 Reports — `/api/reports` (🅰 🅵)

| Method | Path | Returns |
|---|---|---|
| GET | `/reports/types` | list of supported report keys |
| GET | `/reports/audit` | `{entries, byModule, logFile}` |
| GET | `/reports/{type}` | **CSV bytes** with `Content-Disposition` |
| GET | `/reports/{type}?keyword=…&department=…` | filtered CSV |

Supported types — **9** in total:
`students`, `faculty`, `courses`, `classrooms`, `laboratories`,
`equipment`, `resources` (all three resource tables joined), `bookings`,
`activities`.

### 5.12 Users — `/api/users` (🅰)

| Method | Path |
|---|---|
| GET | `/users`, `/users/options`, `/users/{id}` |
| POST | `/users` |
| PUT | `/users/{id}` |
| PUT | `/users/{id}/status` |
| DELETE | `/users/{id}` |

### 5.13 Concurrency test — `/api/concurrency` (🅰)

| Method | Path | Purpose |
|---|---|---|
| GET | `/concurrency/resources` | pickable resources |
| POST | `/concurrency/run` | spawn N threads racing for one slot |
| GET | `/concurrency/count` | total bookings (before/after) |

### 5.14 Misc

| Method | Path | Access |
|---|---|---|
| GET | `/health` | 🔓 `{status:"UP", application, frontend}` |
| GET | `/` | 🔓 → `302 login.html` |

### 5.15 Status code contract

| Code | Meaning |
|---|---|
| 200 / 201 | OK / created |
| 400 | validation failure — body contains `errors: {field: message}` |
| **401** | no session, or token expired/revoked |
| **403** | signed in but the role lacks the permission |
| 404 | record or endpoint not found |
| 405 / 415 | wrong HTTP verb / wrong content type |
| **409** | booking conflict |
| 500 | unexpected server error (detail only in the log) |

---

## 6. Security layer

The authentication/authorisation code lives **only** in
`backend/src/main/java/com/smartcampus/security/` and
`config/WebConfig.java`. It is a self-contained layer with no knowledge of
students, courses, reports or CSV.

### 6.1 Components

| File | Responsibility |
|---|---|
| `security/AuthService.java` | login, logout, `me()`, `status()`, change-password |
| `security/PasswordService.java` | wraps `BCryptPasswordEncoder(10)` — `encode` / `matches` |
| `security/AuthInterceptor.java` | `HandlerInterceptor` on `/api/**` |
| `security/RequireRoles.java` | custom annotation read by the interceptor |
| `security/AuthContext.java` | `ThreadLocal<AuthenticatedUser>` request context |
| `security/AuthenticatedUser.java` | resolved id / name / email / role (never the raw password) |
| `security/Permissions.java` | `forRole(Role)` → permission keys |
| `repository/SessionRepository.java` | token CRUD in `user_sessions` |
| `model/SessionToken.java` | token entity (UUID hex, expiry, user id) |

### 6.2 Login sequence

```
POST /api/auth/login {email, password}
 1. look up users WHERE email = ?                (PreparedStatement)
 2. passwordService.matches(raw, hash)            (BCrypt — constant time)
    ├─ no user  → same generic 401 message (no user enumeration)
    └─ wrong pw → 401 "Invalid email or password"
 3. generate UUID hex token
 4. INSERT INTO user_sessions (token, user_id, expires_at)   ← DB-backed,
    so sessions survive a server restart
 5. read users.role from the DATABASE (never from the request)
 6. return {token, user{id,name,email,role}, permissions[]}
```

### 6.3 Request authorisation sequence

```
ANY /api/** request
 1. AuthInterceptor.preHandle()
    ├─ public path? (/auth/login, /auth/status, /health) → continue
    ├─ no/unknown token → 401
    ├─ expired token    → 401 + row deleted
    └─ else → AuthContext.set(user)  (ThreadLocal)
 2. @RequireRoles on the handler method (or class)
    └─ role not listed → ForbiddenException → 403
 3. service layer ownership check (bookings, /students/me)
 4. AuthContext.clear() in afterCompletion
```

### 6.4 Permission keys

Returned by `/auth/me` and used by the front end **only to hide UI**:

| Role | Keys |
|---|---|
| ADMIN | `dashboard.read`, `students.read/write`, `faculty.read/write`, `courses.read/write`, `classrooms.read/write`, `laboratories.read/write`, `equipment.read/write`, `activities.read/write`, `bookings.read/readAll/write/approve`, `reports.read`, `users.read/write`, `settings.read/write` (24 keys) |
| FACULTY | read keys for all modules, write on `courses`, `activities`, `bookings`, `bookings.approve`, `reports.read` |
| STUDENT | `dashboard.read`, `students.read` (personal view only), `courses.read`, `classrooms.read`, `laboratories.read`, `equipment.read`, `activities.read`, `bookings.read`, `bookings.write`, `settings.read/write` |

**Why a student holds `students.read` but still cannot list the register.**
The key means *"may see student data"*, and for a student that data is their
own row. `GET /api/students` is `@RequireRoles({ADMIN, FACULTY})`, so a request
for the register returns **403**; `students.html` therefore calls
`GET /api/students/me`, which resolves the row **from the session token on the
server** — the browser never supplies a student id. The page renders that one
record read-only.

### 6.5 Security rules that are actually enforced

* ✅ Passwords stored **only** as BCrypt hashes; `password_hash` is never
  serialised — `GET /api/users` returns `UserInfo`, a DTO without that field.
* ✅ The role always comes from `users.role` in the database. A request that
  sends `"role":"ADMIN"` in the body is ignored.
* ✅ The browser never decides authorisation: a student asking
  `GET /api/students` for the register gets **403**, and the one row they are
  allowed to see comes from `/api/students/me`, resolved from their token.
* ✅ No DB credentials, no stack traces, no SQL in any response body.
* ✅ Session tokens are random UUID hex stored server-side; logout deletes the row.
* ✅ Unauthenticated → **401**, insufficient permission → **403** (verified by
  8 automated checks in `tests/api-smoke-test.ps1`).
* ✅ `index.html` and every module page redirect to `login.html` when the
  token is missing or rejected.

> The front end's role checks (`Auth.can(...)`, hiding menu items) are a
> **convenience only**, and are labelled as such in `frontend/js/auth.js`.

---

## 7. Concurrency & double-booking control

This is the headline requirement: *no two overlapping bookings for the same
resource may ever be committed, even under a genuine thread race.*

### 7.1 Algorithm (`service/BookingServiceImpl.java`)

```java
String key = resourceType + "#" + resourceId + "#" + date;
ReentrantLock lock = LOCKS.computeIfAbsent(key, k -> new ReentrantLock());
lock.lock();
try {
    TransactionExecutor.doInTransaction(() -> {
        // (1) SELECT overlapping bookings inside the transaction
        //     WHERE resource = ? AND date = ?
        //       AND start < ?newEnd AND end > ?newStart
        if (overlapFound) throw new BookingConflictException(...);  // → HTTP 409
        // (2) INSERT the new booking in the SAME transaction
        return bookingRepository.insert(...);
    });
} finally {
    lock.unlock();
}
```

* **Lock striping** — a `ConcurrentHashMap<String, ReentrantLock>` holds one
  lock per `resourceType#resourceId#date`. Two bookings for *different*
  resources or *different* days never block each other, so throughput stays high.
* **Atomicity** — the conflict `SELECT` and the `INSERT` share one JDBC
  transaction (`TransactionExecutor.doInTransaction`), so a reader can never
  observe a half-written state.
* **Unique references** — `synchronized String nextBookingRef()` hands out
  `BK-%04d` from a `long bookingSequence` seeded from `max(booking_id)` at
  startup, guaranteeing no duplicate reference.
* **Detection is inclusive-exclusive** — `start < newEnd AND end > newStart`,
  so `10:00–12:00` and `12:00–14:00` are both allowed (adjacent), while
  `11:00–13:00` is rejected (partial overlap).

### 7.2 The error message

`BookingConflictException` maps to **HTTP 409** and produces:

```
Booking conflict: LAB-02 - Database Systems Lab is already booked from 10:00 to 12:00
(BK-0003). Please choose a different time.
```

The message is built from the *actual conflicting row* — resource label, real
start/end times and the conflicting booking reference. The front end displays
it verbatim (red alert inside the booking modal + toast).

### 7.3 The multithreaded demonstration

`service/ConcurrencyDemoService.java` (ADMIN only, `POST /api/concurrency/run`):

1. Builds one `Booking` request for a chosen resource/date/slot.
2. Captures `AuthContext.get()` on the caller thread — `AuthContext` is a
   `ThreadLocal`, so worker threads would otherwise look anonymous and be
   rejected with 401 instead of 409. It is installed (`set`) inside each
   worker and `clear()`ed in the `finally` block.
3. Creates **N `Thread` objects** (default 5) and blocks them all on a
   `CountDownLatch` **start line**.
4. Releases the latch → all N threads call `bookingService.create(...)`
   within microseconds of each other.
5. Exactly **one** wins the `ReentrantLock` and commits; the rest receive
   `BookingConflictException`.
6. Each thread appends its outcome to a shared `List<String>` inside
   `synchronized (outcomes)` and counts down a second latch.
7. The controller returns:

```json
{
  "resource": "LAB-02 - Computer Programming Lab",
  "attempts": 5, "succeeded": 1, "failed": 4,
  "elapsedMillis": 6,
  "outcomes": [
    "BookingThread-3: SUCCESS - created BK-0020 for LAB-02 - Computer Programming Lab 09:00-11:00",
    "BookingThread-1: REJECTED - Booking conflict: LAB-02 - Computer Programming Lab is already booked ...",
    "BookingThread-2: REJECTED - Booking conflict: ...",
    "BookingThread-4: REJECTED - Booking conflict: ...",
    "BookingThread-5: REJECTED - Booking conflict: ..."
  ]
}
```

**Verified result from the automated suite:**
`5 threads → succeeded = 1, failed = 4`. ✔

---

## 8. File handling

| Feature | Implementation | Output |
|---|---|---|
| CSV report generation | `ReportService` + `BufferedWriter`/`FileWriter` | `backend/exports/students-2026-10-03.csv` |
| CSV download | file re-read as bytes → `Content-Disposition: attachment` | browser download |
| Audit trail file | `AuditLogService` appends with `BufferedWriter` | `backend/logs/activity-2026-10-03.log` |
| Directory creation | `Files.createDirectories(...)` at startup | auto-created |
| Overloading demo | `export(type)` / `export(type, criteria)` | same file, filtered rows |

Both directories are listed in `application.properties`
(`app.export-dir`, `app.log-directory`) and are regenerated on every request.

---

## 9. Front end design

### 9.1 Files

```
frontend/
├── login.html            public
├── index.html            dashboard (Chart.js)
├── students.html         faculty.html      courses.html
├── classrooms.html       laboratories.html equipment.html
├── activities.html       bookings.html     reports.html
├── users.html            settings.html
├── css/  style.css · dashboard.css · responsive.css · theme.css
│         login3d.css (3D room behind the form) · lightcord.css (pull-cord switch)
└── js/   api.js · common.js · auth.js · shell.js · theme.js
          login.js · lightcord.js · dashboard.js · students.js … (one per page)
```

### 9.2 Shared modules

| File | Provides |
|---|---|
| `js/api.js` | `API_BASE` (auto-switch for Live Server), `apiRequest`, `apiEnvelope`, `queryString`, `api.get/post/put/del`, `apiGet`, `ApiError`, token storage, **401 → clear session + redirect to `login.html?expired=1`** |
| `js/common.js` | `toast()`, `confirmDialog()`, `openModal/closeModal`, `resetForm`, `showFieldErrors`, `statusBadge`, `codeBadge`, `formatDate/Time`, `escapeHtml`, `withBusy`, `emptyBlock/errorBlock/loadingBlock`, `downloadCsv`, and **`class CrudTable`** — the reusable search + filter + sort + pagination table with loading/empty/error states |
| `js/auth.js` | `Auth.requireAuth()`, `Auth.can()`, `Auth.role()`, `Auth.logout()`, UI role gating — explicitly documented as convenience, not security |
| `js/shell.js` | `AppShell.init(page, title)` renders the sidebar (single `MENU` declaration), top bar with **working global search**, mobile toggle, sign-out |
| `js/theme.js` | the **`MODULE_THEMES` colour map** (one entry per existing module), `Theme.cardVars()`, the background-style selector (default / light / dark / blue / green / purple), the **round theme-colour palette** (11 swatches + a native `<input type="color">`, remembered in `sc.theme.color`), the full-page `.theme-wash` tint layer, pointer tilt on the tiles and the click that applies a module's theme before the existing link navigates |
| `js/lightcord.js` | the pull-the-cord switch on `login.html`: pointer / touch / keyboard toggling of `#authPage[data-light]`, the spring-back and the click guard — deliberately **no** storage, session or authentication side effects |

### 9.3 UI states

Every page provides: **loading spinner**, **empty state** (friendly message +
suggestion), **error state** (message + "Try again" button), **toasts** for
success/failure, **confirm dialogs** before any destructive action, and
**field-level validation messages** returned by the server.

### 9.4 Responsive

`css/responsive.css` gives a fixed sidebar ≥992 px and an off-canvas drawer
with overlay below it, a collapsing top bar, `.hide-mobile` table columns and
full-width page actions on phones.

### 9.5 Role gating in the UI

The sidebar is built from a single `MENU` array in `js/shell.js`; each entry
carries a permission key and is simply not rendered when `Auth.can(key)` is
false. Pages that a role may open but not *use* hide their Create/Edit/Delete
controls (`canCreate`, button `display`).

Four pages carry an `#accessDenied` panel that is revealed when the
signed-in user lacks the module's read permission — the pattern is identical
in all of them:

| Page | Denied to | Permission checked |
|---|---|---|
| `faculty.html` | STUDENT | `faculty.read` |
| `users.html` | FACULTY, STUDENT | `users.read` |
| `reports.html` | STUDENT | `reports.read` |
| `students.html` | defensive fallback | `students.read` |

`students.html` is not really denied to anyone: all three roles hold
`students.read`. A STUDENT however cannot call the register endpoint
(`GET /api/students` is `@RequireRoles({ADMIN, FACULTY})`), so instead of
rendering an error state the page switches to a **personal read-only view** —
it calls `GET /api/students/me`, whose row is resolved server-side from the
session token, and shows that single record with a *View full details* button
that reuses the normal view modal. The panel remains as the fallback for a
login account with no linked student record.

Every one of these is **only** a convenience. Opening the panel does not grant
anything: the same request sent straight to the API is refused with **403** by
`@RequireRoles`, and `tests/ui-smoke-test.ps1` asserts the panel appears for
the right role while `tests/api-smoke-test.ps1` asserts the API refuses.

### 9.6 Dashboard theming & 3D tiles

`css/theme.css` + `js/theme.js` are additive: they never replace the existing
markup, routes or styles.

| Piece | How it works |
|---|---|
| **Colour map** | one `MODULE_THEMES` table in `js/theme.js` maps every existing module page → accent hex, RGB triplet, pastel wash, chip tint and page-wash strength. No colour is written twice. |
| **CSS variables** | the map is published as `--theme-primary`, `--theme-accent`, `--theme-accent-rgb`, `--theme-soft`, `--theme-tint`, `--theme-shadow`, `--theme-background`, `--theme-card`, `--theme-border` on `<html>`; components keep reading the existing `--sc-*` tokens, so nothing is re-styled rule by rule. |
| **Click a tile** | the tile keeps its plain `<a href="students.html">`. `theme.js` applies that module's theme, marks the tile `.is-active` (+ `aria-current` + a check badge), and only then lets the link navigate — same page, same route, same API. Modifier clicks and *Open in new tab* are left untouched. |
| **Background tint** | a fixed `.theme-wash` layer (injected once by `theme.js`) cross-fades to the module colour, so the canvas is tinted rather than flooded; the sidebar pill, top-bar rule, chips and avatar follow the same accent. |
| **Persistence** | the selection lives in `localStorage` (`sc.theme.module`); every module page applies *its own* colour, so returning to the dashboard always shows the last module you were in. The optional global colour is a second key, `sc.theme.color` (`''` = module default) — nothing else is written. |
| **Theme colour picker** | a second group in the same dropdown, *Choose Your Theme Color*: eleven round CSS swatches (Blue `#3B82F6`, Green `#22C55E`, Purple `#8B5CF6`, Orange `#F97316`, Red `#EF4444`, Pink `#EC4899`, Yellow `#EAB308`, Teal `#14B8A6`, Indigo `#6366F1`, Dark `#1F2937`, White `#FFFFFF`) drawn as pure CSS circles — no emoji — plus a final **“+”** swatch that opens the native `<input type="color">` for any custom hex. The picked swatch carries a checkmark **and** a focus ring (never colour alone) with a *Selected: Blue* readout, and the choice is remembered in `sc.theme.color` across reloads. It repaints the **shared** elements (sidebar pill, avatar, top bar, greeting, Theme button, charts) while each module keeps its own colour for active/selected states — so the 3D tiles, greetings, navigation, APIs/CRUD and `prefers-reduced-motion` behaviour are all unaffected. The same palette is mounted as a compact floating picker on `login.html` and on module pages without the dashboard menu, using the same swatches, `aria-checked` state, arrow-key navigation and `sc.theme.color` key; on login it also repaints the room canvas, brand wall and primary button. |
| **3D tiles** | `perspective()` + `rotateX/rotateY/translateY` driven by CSS variables (`--tilt-x`, `--tilt-y`, `--lift`, `--sheen-x/y`), a pointer-tracked specular sheen, layered contact/mid-air/cast shadows, a floating icon and a pressed `:active` state — transforms only, so **no layout shift**. |
| **Background styles** | optional palette button on the dashboard → Default / Light / Dark / Blue / Green / Purple (`html[data-style]` + `data-bs-theme` for Bootstrap), stored in `sc.theme.style`, with a *Reset accent* row (clears the module accent **and** the global colour). |
| **Accessibility** | keyboard focus shows the same lift plus a solid outline, the active state is never signalled by colour alone, and `prefers-reduced-motion` removes tilt/float while keeping colour changes and navigation. |

### 9.7 Login light cord

`login.html` opens **dark**. `css/lightcord.css` + `js/lightcord.js` add a
hanging bulb with a braided cord and a brass grip, absolutely positioned at the
top centre of the room:

| Piece | How it works |
|---|---|
| **State lives on the page, not in the session** | the whole scene is one wrapper — `<div class="auth-page" id="authPage" data-light="off|on">` — and every reveal is pure CSS (`opacity` / `transform` / `visibility`), so the glass card fades in with the *Welcome Back!* heading when the light comes on and disappears when it goes off. A `<noscript>` block shows the form for browsers without JavaScript. |
| **Pulling** | pointer drag with a scale-aware threshold (a plain click on the grip toggles too), touch drag that never steals the page scroll, and a real `<button>` wrapping cord + grip so `Enter`/`Space` and a visible focus ring work. Releasing mid-drag springs the cord back; the rig scales itself on short viewports (`--rig-scale`). The handle exposes `aria-expanded` and `aria-controls`, and the hidden card is `inert` + `aria-hidden` until the light comes on, so keyboard and screen-reader users meet a clear switch rather than a buried form. |
| **Never an authentication** | the script reads/writes no storage, no session and no form values — pulling the cord cannot sign anyone in, credentials typed in survive any number of toggles, and the existing show/hide password, loading and error states are untouched. |
| **Reduced motion & small screens** | `prefers-reduced-motion` drops the swing/stretch and switches state instantly; below 992 px the brand panel is gone, the room fills the screen and the decorative blueprint is clipped (`overflow-x: clip`) so the page never scrolls sideways. |

### 9.8 Sign-out light switch

`js/shell.js` replaces the generic confirm box with a native modal `<dialog>`
built on demand, so every protected page gets the same interaction without new
markup.

| Piece | How it works |
|---|---|
| **Prompt** | *Ready to switch off and sign out, {first name}?* with a hanging bulb and cord, **Stay signed in** and **Sign out** buttons, plus a close control. |
| **Cancel** | *Stay signed in*, the close button, `Escape`, or the backdrop all dismiss the dialog and leave the page untouched. Focus returns to the sidebar sign-out button. |
| **Focus** | `showModal()` provides the modal semantics; focus moves to **Stay signed in** (the safe action), Tab is explicitly wrapped inside the dialog, and the dialog is labelled by its heading and description. |
| **Confirm** | The bulb switches off and the interface dims for ~720 ms, then the existing `Auth.logout()` runs unchanged: `POST /api/auth/logout`, local session clear, redirect to `login.html?signedout=1`. The timer always fires, so a failed animation can never block sign-out. |
| **Reduced motion & failure** | The exit is shortened to 40 ms under `prefers-reduced-motion`; if logout fails, the dim state is removed and the existing `toastError(...)` behaviour reports it while the session remains usable. |

---

## 10. Testing

### 10.1 Automated API suite

```powershell
# backend must already be running
./tests/api-smoke-test.ps1
```

**68 checks**, grouped as:

| Group | Examples |
|---|---|
| Auth (9) | login with `email` field, wrong password → 401, `/auth/me`, `/auth/status` anonymous & signed in, all three demo roles |
| Authorisation (8) | no token → 401 (list + dashboard), student reading register → 403, student creating → 403, faculty reading users → 403, student CSV export → 403, faculty concurrency run → 403, `/health` public |
| List/search/filter/sort/page (13) | paging envelope, keyword search, multi-filter, sort desc, **malicious `sort=DROP TABLE` whitelisted**, `/options`, and the list endpoint of every module |
| CRUD (8) | create → read → update → delete → 404 after delete, **duplicate USN rejected**, invalid input → 400 with `errors` map, **a student reading somebody else's profile → 403** |
| Bookings (8) | resource picker, create, **duplicate booking → 409**, partial overlap → 409, adjacent slot accepted, availability `available:false`, availability `available:true`, repeatable cleanup of leftovers |
| Concurrency (3) | **5 threads → exactly 1 success**, 4 rejected, 5 outcome lines |
| Reports (12) | CSV bytes for all **9** types (`students, faculty, courses, classrooms, laboratories, equipment, resources, bookings, activities`), filtered CSV, audit trail, report types |
| Dashboard (3) | statistics + all group-by maps + events + recent bookings |
| Users (2) | list users, **no `password` field in the payload** |
| Session (2) | logout → 200, token invalid after logout → 401 |

Expected output:

```
TOTAL: 68  PASS: 68  FAIL: 0
```

### 10.2 Frontend static check

```powershell
# backend must be running (it also fetches the pages over HTTP)
./tests/frontend-check.ps1
```

**102 checks** that need no browser at all:

| Group | What it proves |
|---|---|
| Pages served (26) | every `.html` returns HTTP 200 from Spring Boot, and the **6** CSS (`style · dashboard · responsive · theme · login3d · lightcord`) + **7** shared JS (`api · common · auth · shell · theme · login · lightcord`) assets exist |
| Asset links (13) | every local `<link href>` / `<script src>` in every page resolves to a real file |
| DOM id consistency (39) | every `document.getElementById('…')` in a page's script exists in that page's HTML — the classic typo that would throw at runtime. Each module page must also carry `#sidebar`, `#topbar`, `#appFooter`, `#sidebarOverlay` and call `Auth.requireAuth()` + `AppShell.init(...)`; `login.js` must be present and `login.html` must link the **light-cord scene** (`css/lightcord.css` + `js/lightcord.js`) |
| Sidebar targets (12) | every destination declared in the single `MENU` array in `js/shell.js` is a file that really exists — **no dead menu links** |
| Script order (12) | `api.js` → `common.js` → `auth.js` → `shell.js` → page script, in that order, on every page |

Expected output: `TOTAL: 102  PASS: 102  FAIL: 0`

### 10.3 Headless-browser UI smoke test

```powershell
./tests/ui-smoke-test.ps1            # all 3 roles x 12 pages
./tests/ui-smoke-test.ps1 student    # one role only
```

This one drives a **real Chrome (headless)** — it is not a mock:

1. opens `frontend/_uitest.html?as=<role>&next=<page>` (the script copies this
   harness in from `tests/_uitest.html` and deletes it afterwards);
2. the harness performs a **genuine `POST /api/auth/login`**, stores the real
   session token the same way `login.js` does, then redirects to the page;
3. the rendered DOM and the browser console are captured;
4. assertions per page: **no JavaScript exception**, the shared app shell
   rendered, the page's own content marker is present, the user was not bounced
   back to `login.html`, and — for the nine list pages — **at least one real
   database row rendered**;
5. a final authorisation pass proves each role is *denied* where it must be
   (`faculty.html`, `users.html`, `reports.html` for STUDENT; `users.html`
   for FACULTY) by requiring the `#accessDenied` panel to be visible — and
   that every other page is *not* wrongly denied.

It needs Chrome (or Edge) and the backend; dumps are written to
`%TEMP%\sc-ui-dump\` for inspection.

Expected output (36 page loads):

```
TOTAL: 196  PASS: 196  FAIL: 0
```

### 10.4 Login light-cord & theme-colour suite

```powershell
# backend must be running (the suite really signs in)
./tests/login-light-smoke-test.ps1
```

A second headless-Chrome suite — **55 checks** — driven through
`tests/_lighttest.html`, a harness that loads the **real `login.html`** inside
an iframe. It is the regression net for the two new interactions:

| Group | What it proves |
|---|---|
| Light starts OFF | `#authPage[data-light="off"]` on arrival, the hint reads *Pull the cord to turn on the light.*, the glass card is hidden |
| Pull → ON | dragging the handle stretches the cord, the bulb swings, `data-light` becomes `on`, the card fades in with the *Welcome Back!* heading |
| Pull → OFF → ON | a second pull hides the form again and a third lights it up — a pure toggle |
| Credentials survive | text typed into e-mail/password is still there after every toggle; the switch never signs anyone in |
| Auth untouched | the existing client validation still fires, a wrong password surfaces the server's usual error, a correct one performs a **real redirect** to `index.html` |
| Theme colour | 11 round CSS swatches (not emoji) plus the **“+”** `<input type="color">`, clicking *Green* paints the accent, `Selected: Green` read-out, module identity kept for active states, `sc.theme.color` written and restored after a reload, only `sc.theme.*` keys touched, **no password in localStorage** |
| Clean run | zero JavaScript exceptions |

Expected output:

```
TOTAL: 55  PASS: 55  FAIL: 0
```

### 10.5 Manual test script

**A. Authentication**

1. Open `http://localhost:8080/login.html` → the only page reachable without a session.
2. Submit `admin@smartcampus.com` / `Admin@123` → redirected to the dashboard with a success toast.
3. Submit a wrong password → red alert + toast, password cleared, **HTTP 401**.
4. Open the browser dev tools → Network: the response body contains `user.role = "ADMIN"` and **no** `password` field.
5. Click **Sign out** → the light-switch dialog opens (focus on *Stay signed in*) → cancel with Escape and confirm the page is unchanged, then open it again and choose **Sign out** → the dashboard dims and returns to `login.html?signedout=1`.
6. Press Back on the dashboard → redirected to `login.html` (session gone).
7. Change the system clock / wait for expiry, or log out in a second tab → the first tab shows *session expired* and redirects.

**B. Role matrix**

| Action as STUDENT | Expected |
|---|---|
| open `students.html` | sidebar hides the item; typing the URL directly → the table shows a **403** error state |
| `POST /api/students` via curl | **403** |
| open `bookings.html` and create a booking | allowed — but the list only contains *their own* rows |
| approve a booking | no Approve button; calling `PUT /api/bookings/1/status` directly → **403** |

| Action as FACULTY | Expected |
|---|---|
| read students/faculty/courses | allowed |
| create a course | allowed |
| open `users.html` | hidden; direct call → **403** |
| run the concurrency test | hidden; direct call → **403** |

| Action as ADMIN | Expected |
|---|---|
| everything above | allowed |
| create / update / delete any record | allowed |
| export any CSV, view the audit trail | allowed |

**C. Booking conflict (the required error message)**

1. Sign in as **ADMIN** → `bookings.html` → **New booking**.
2. Choose **LAB-02**, tomorrow, `10:00` → `12:00`, purpose `Conflict demo` → Save.
   → green toast: booking reference `BK-00xx`.
3. Repeat **exactly the same** reservation → red alert and toast:

   ```
   Booking conflict: LAB-02 - Database Systems Lab is already booked from 10:00 to 12:00 (BK-00xx). Please choose a different time.
   ```

   and in dev tools the response is **HTTP 409**.
4. Try `11:00 → 13:00` (partial overlap) → also **409**.
5. Try `12:00 → 14:00` (adjacent) → **succeeds**.
6. In the availability checker ask for `16:00 → 17:00` → green *"available"*.

**D. Multithreading demonstration**

1. Sign in as **ADMIN** → `bookings.html` → **Double-booking race test** card.
2. Pick any resource, tomorrow, `09:00 → 11:00`, attempts `5` → **Run test**.
3. Exactly **one** green row `SUCCESS` and **four** red rows `REJECTED - Booking conflict…`;
   summary reads `succeeded: 1, failed: 4`.
4. Click **Refresh** on the table → exactly **one** new booking exists.

**E. CRUD & UI states**

1. `students.html` → **Add student** → leave USN empty → inline validation.
2. Save a valid record → toast, row appears immediately after refresh.
3. Edit it → changes persist across a browser reload (real `UPDATE`).
4. Delete → confirmation dialog → toast → row disappears.
5. Search a nonsense string → **empty state**; stop the backend → **error state** with *Try again*.
6. Change page size, page number, sort order and every filter — all reflect in
   the URL query and in the SQL (`WHERE` clause visible in the server log).
7. **Export CSV** → a real `.csv` file downloads and also appears in
   `backend/exports/`.

**F. Dashboard**

1. Open the dashboard → cards count up, four charts render.
2. Add a student, then reload → *Students* card and *Students per department* chart increase.
3. Approve a booking → *Bookings by status* doughnut changes.
4. Nothing on the page is a static number — inspect the network tab: one call
   to `/api/dashboard/statistics`.

**G. Responsiveness**

1. Resize to 375 px (phone) → sidebar becomes a hamburger drawer, tables scroll
   horizontally, cards go 2-up, page actions become full width.
2. 768 px (tablet) → 3-column cards, reduced columns.
3. Print the reports page → sidebar/top bar hidden.
4. `login.html` on a phone → the brand panel is gone, the room and cord fill the
   screen and **nothing scrolls sideways** (the form is reachable by scrolling
   down only).

**H. Login light cord**

1. Open `http://localhost:8080/login.html` → the room is **dark**, the bulb is
   off and the sign-in form is hidden; the hint reads
   *Pull the cord to turn on the light.*
2. Drag the brass handle downward (or tap it / focus it and press `Enter`) →
   the cord stretches, the bulb swings, the room glows warm and the
   **Welcome Back!** card fades in. Any text you had typed is still there.
3. Pull again → light off, card gone; a third pull → light on again.
4. Let go mid-drag → the cord springs back; `Tab` reaches the handle with a
   visible focus ring; with *prefers-reduced-motion* the scene simply switches
   state with no swing animation.
5. Sign in with a wrong password → the usual error appears; with the right one
   → a real redirect to the dashboard. **The cord is only ever an animation.**

**I. Theme colour picker**

1. Dashboard → **Theme** button → *Choose Your Theme Color*.
2. Click **Green** → sidebar pill, avatar, greeting, buttons and charts follow;
   the swatch shows a checkmark **and** a ring plus the read-out
   *Selected: Green*.
3. Reload → still green (remembered in `sc.theme.color`); **Reset accent** →
   back to *Module default*.
4. The last **+** swatch opens the native colour chooser — any custom hex is
   remembered the same way, and the active module keeps its own accent for
   selected tiles and chips.

---

## 11. How to run the project

Short version (full detail in `README.md`):

```bash
# 1. database (one time)
mysql -u root -p < database/schema.sql
mysql -u root -p smart_campus_db < database/sample_data.sql

# 2. backend
cd backend
mvn spring-boot:run

# 3. front end — either just open:
#    http://localhost:8080/login.html
#    …or right-click frontend/login.html → "Open with Live Server" in VS Code
```

| Role | Email | Password |
|---|---|---|
| Administrator | `admin@smartcampus.com` | `Admin@123` |
| Faculty | `faculty@smartcampus.com` | `Faculty@123` |
| Student | `student@smartcampus.com` | `Student@123` |

---

## 12. Limitations and future enhancements

### 12.1 Current limitations

| # | Limitation | Why it is like that |
|---|---|---|
| 1 | **Email is not unique in `students`/`faculty`** — only `USN`/employee code is | modelling the real university rule where the college e-mail is derived later |
| 2 | **No password reset by e-mail** | requires an SMTP server; deliberately out of scope so the project runs offline |
| 3 | **Session tokens never expire while `rememberMe` is on** beyond the configured TTL | simple fixed TTL (12 h) instead of sliding windows / refresh tokens |
| 4 | **One admin can delete another admin** — only *self*-protection is enforced client-side | server accepts it; a real system needs a super-admin concept |
| 5 | **Bookings cannot be edited after creation** (only status changes / cancel / delete) | avoids reopening the conflict window on an existing reservation |
| 6 | **No file upload** (profile photos, lab manuals) | storage backend not specified |
| 7 | **CSV export uses comma only**, no Excel (`xlsx`) rendering | `BufferedWriter` was chosen deliberately to demonstrate plain file handling |
| 8 | **Concurrency locks are per-JVM** | a single-node deployment; multi-instance would need a DB `SELECT … FOR UPDATE` or a distributed lock |
| 9 | **No browser-interaction tests for the CRUD flows** (clicking Save in a real browser, modal flows) | covered as far as practical by `frontend-check.ps1` (static) + `ui-smoke-test.ps1` (real headless Chrome render per page per role); the light-cord drag and the colour swatches *are* driven end-to-end by `login-light-smoke-test.ps1`; full CRUD click-through would need Playwright/Cypress |
| 10 | **Audit log grows unbounded** | one file per day, no rotation policy yet |
| 11 | **Charts have no date range picker** | dashboard aggregates are all-time |
| 12 | **`GET /students/{id}` allows FACULTY to read any student** while students can read only their own | intentional staff privilege |
| 13 | **No HTTPS** | runs on `http://localhost`; a reverse proxy (nginx + Let's Encrypt) would be needed in production |

### 12.2 Future enhancements

**Security**

1. Refresh tokens with rotation + reuse detection, and a session-expiry countdown in the UI.
2. `SELECT … FOR UPDATE` / optimistic `version` column as a second line of defence behind the in-JVM lock.
3. Rate limiting on `/api/auth/login` and account lockout after N failures.
4. Password reset tokens (single-use, time-limited) stored in `user_sessions`.
5. Full audit of *who changed what field*, not just *which module was touched*.
6. HTTPS + HSTS, strict CSP headers, and rotating BCrypt cost.

**Functionality**

7. Timetable view (weekly grid) per classroom/lab built from the bookings table.
8. Recurring bookings (weekly series) and auto-expiry of stale `PENDING` rows.
9. Waitlist: when a slot is full, allow queued requests that are auto-approved on cancellation.
10. Attendance & grade modules reusing the existing `Person` / `Course` hierarchy.
11. Email / in-app notifications on approval, rejection and cancellation.
12. Equipment maintenance history table (currently only the current condition).
13. Certificate / ID-card PDF generation (iText) next to the CSV exporter.
14. Push notifications for booking reminders.

**Architecture**

15. Split the monolith into `auth-service` + `resource-service` + `booking-service`.
16. Replace hand-written paging with a single `Pageable` abstraction once JDBC is demonstrated.
17. Redis cache for dashboard aggregates with explicit invalidation on writes.
18. WebSocket live update of the bookings table (so two admins see each other's conflicts instantly).
19. Add a proper test layer: JUnit 5 + Mockito service tests and Spring MockMvc controller tests.
20. Containerise (`Dockerfile` + `docker-compose.yml` with MySQL) for one-command startup.
21. CI pipeline (GitHub Actions) running `mvn verify` + the 68-check API suite.
22. Front-end component framework (React/Vue) or a build step with bundling and linting.

**UX / analytics**

23. Date-range picker and drill-down on every dashboard chart.
24. Keyboard shortcuts, command palette and saved/favourite filters.
25. Accessible (WCAG 2.1 AA) audit: ARIA labels, focus trapping in modals, contrast pass.
26. Progressive Web App (offline shell + service worker) so students can view schedules offline.
27. Multi-language (i18n) support.

---

*End of document.*
