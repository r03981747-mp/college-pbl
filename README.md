# Smart Campus — Integrated Management System

A complete full-stack **Object-Oriented Programming** project: a real REST API
(Spring Boot + MySQL + hand-written JDBC) with a professional single-page-style
Bootstrap front end.

It manages **students, faculty, courses, classrooms, laboratories, equipment,
campus activities, resource bookings, login accounts, reports and a live
dashboard** — every button in the UI performs a real database operation.
Nothing on screen is fake or hardcoded.

---

## 1. What is inside

| Layer | Technology |
|---|---|
| Front end | HTML5, CSS3, JavaScript (ES2020), **Bootstrap 5.3**, **Bootstrap Icons**, **Chart.js 4**, Fetch API |
| Back end | **Java 25**, **Spring Boot 3.5.16**, Spring Web, Spring JDBC, Spring Security **Crypto** (BCrypt only), Maven |
| Database | **MySQL 8** (`smart_campus_db`), plain `java.sql.PreparedStatement` (no JPA, no MyBatis) |
| Security | Separate auth layer: BCrypt hashing, DB-backed session tokens, `HandlerInterceptor` + `@RequireRoles`, 401 / 403 |

### Modules

| # | Page | What you can do |
|---|---|---|
| 1 | `index.html` | Live dashboard — cards, 4 charts, upcoming events, latest bookings |
| 2 | `students.html` | Add / view / search / update / delete students, filter + sort + paging. A **student** signing in gets a read-only card of their *own* record instead (the register answers 403) |
| 3 | `faculty.html` | Manage teaching staff |
| 4 | `courses.html` | Manage courses, assign / unassign a teacher |
| 5 | `classrooms.html` | Manage rooms, check availability |
| 6 | `laboratories.html` | Manage labs, check availability |
| 7 | `equipment.html` | Manage equipment, change condition / quantity, check availability |
| 8 | `activities.html` | Manage campus events with an upcoming-events strip |
| 9 | `bookings.html` | Reserve any resource, see **409 conflict** messages, run the **multithreaded race test** |
| 10 | `reports.html` | Preview + download CSV reports, view the audit trail |
| 11 | `users.html` | Administrator: create / disable / delete login accounts |
| 12 | `settings.html` | Change password, account summary, session info, sign out |
| 13 | `login.html` | The only public page — a dark room with a hanging bulb: **pull the light cord** (drag, tap or `Enter`) to switch the bulb on and reveal the sign-in form. It is a pure animation, never an authentication |

---

## 2. Prerequisites

Install these once:

1. **JDK 25 (LTS)** — <https://adoptium.net>
2. **Maven 3.9+** — <https://maven.apache.org> (or use the wrapper-free `mvn` command below)
3. **MySQL 8** — <https://dev.mysql.com/downloads/installer/>
4. **VS Code** with the extensions *Extension Pack for Java* and *Live Server* (optional — see §5)

Check from a terminal:

```bash
java -version     # 25.x
mvn -version      # 3.9.x
mysql --version   # 8.x
```

> **Windows note.** If `java` / `mvn` are not on the PATH, either tick
> *"Add to PATH"* during installation or run VS Code as a terminal with the
> full path, e.g.
> `"C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe" -version`.

---

## 3. Create the database (one time)

Open a terminal and start MySQL, then load the two SQL files **in this order**:

```bash
mysql -u root -p
```

```sql
SOURCE D:/college pbl/database/schema.sql;
SOURCE D:/college pbl/database/sample_data.sql;
SELECT COUNT(*) FROM students;   -- 12
```

Or in one shot from the project root:

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p smart_campus_db < database/sample_data.sql
```

`schema.sql` creates 11 tables (students, faculty, courses, classrooms,
laboratories, equipment, activities, bookings, users, user_sessions,
activity_logs) with foreign keys, indexes and CHECK constraints.
`sample_data.sql` inserts **12 students, 6 faculty, 10 courses, 7 classrooms,
5 laboratories, 12 equipment items, 8 activities, 10 bookings and 7 login
accounts** — including the three demo users below.

### Database credentials

The backend reads them from `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/smart_campus_db?...
spring.datasource.username=root
spring.datasource.password=root
```

Change **only those three lines** if your MySQL user or password is different.
No database password is ever sent to the browser.

---

## 4. Run the back end

```bash
cd backend
mvn spring-boot:run
```

Wait for:

```
Started SmartCampusApplication in ... seconds
Tomcat started on port 8080 (http) with context path '/'
```

Quick health check: <http://localhost:8080/api/health>

Stop with `Ctrl + C`.

---

## 5. Run the front end — two options

### Option A — simplest (recommended for the demo)

The back end also serves the `frontend/` folder, so just open:

```
http://localhost:8080/login.html
```

No second server, no CORS problems.

### Option B — VS Code Live Server (as required by the brief)

1. Open the folder `D:\college pbl` in VS Code.
2. Right-click `frontend/login.html` → **Open with Live Server**.
3. The page opens on `http://127.0.0.1:5500/login.html`.

The API calls automatically switch to `http://localhost:8080/api`
(see `frontend/js/api.js`), and the back end allows that origin through CORS.

---

## 6. Demo logins

| Role | Email | Password |
|---|---|---|
| Administrator | `admin@smartcampus.com` | `Admin@123` |
| Faculty | `faculty@smartcampus.com` | `Faculty@123` |
| Student | `student@smartcampus.com` | `Student@123` |

The login page has one-click **“Use”** buttons that fill the form for you.
The room starts **dark** — pull the light cord (drag the brass handle, tap it,
or press `Enter` on it) to switch the bulb on and reveal the form; pulling again
turns it off.

---

## 7. Project structure

```
smart-campus/
├── database/
│   ├── schema.sql              # 11 tables, FKs, indexes, CHECKs
│   └── sample_data.sql         # seed rows + BCrypt demo passwords
│
├── backend/
│   ├── pom.xml                 # Spring Boot 3.5.16, Java 25
│   ├── src/main/resources/application.properties
│   ├── exports/                # generated CSV files (file handling)
│   ├── logs/                   # activity-YYYY-MM-DD.log (file handling)
│   └── src/main/java/com/smartcampus/
│       ├── SmartCampusApplication.java
│       ├── config/WebConfig.java            # interceptor + CORS + static UI
│       ├── controller/  (13 REST controllers)
│       ├── service/     (business rules, locks, reports, audit log)
│       ├── repository/  (GenericRepository<T,ID> + JDBC)
│       ├── model/       (entities + enums)
│       ├── dto/         (API request/response objects)
│       ├── security/    (auth: interceptor, tokens, BCrypt, permissions)
│       ├── exception/   (custom exceptions + global handler)
│       └── utility/     (SqlCriteria, TransactionExecutor, Validators)
│
├── frontend/
│   ├── *.html                  # 13 pages
│   ├── css/  style.css · dashboard.css · responsive.css · theme.css · login3d.css · lightcord.css
│   └── js/   api.js · common.js · auth.js · shell.js · theme.js · lightcord.js · + one file per page
│
├── tests/api-smoke-test.ps1      # 68 automated API checks
│   tests/frontend-check.ps1      # 102 static/served frontend checks
│   tests/ui-smoke-test.ps1       # headless-Chrome render test (3 roles x 12 pages, 196 checks)
│   tests/login-light-smoke-test.ps1  # 55 login light-cord + theme-colour checks
│   tests/_uitest.html            # login harness used by ui-smoke-test
│   tests/_lighttest.html         # login harness used by login-light-smoke-test
├── README.md                   # this file
└── PROJECT_DOCUMENTATION.md    # OOP mapping, API list, testing, limitations
```

---

## 8. Run the automated tests (optional)

All four need the back end running. From PowerShell, in the project root:

```powershell
# 1. REST API  - 68 checks
./tests/api-smoke-test.ps1

# 2. Frontend static/served checks - 102 checks (no browser needed)
./tests/frontend-check.ps1

# 3. Real headless Chrome: renders every page as every demo role
./tests/ui-smoke-test.ps1

# 4. Login light cord + theme colour picker - 55 checks (headless Chrome)
./tests/login-light-smoke-test.ps1
```

**1 — API suite** performs 68 checks: login, 401/403 role matrix,
paging/filter/sort, CRUD, booking conflict (409), the multithreaded race test,
CSV exports, dashboard, audit trail and session logout:

```
TOTAL: 68  PASS: 68  FAIL: 0
```

**2 — Frontend check** verifies every page is served, every `<link>`/`<script>`
resolves, every `getElementById(...)` target exists in that page's HTML, every
sidebar link points at a real file, the shared scripts load in the right
order, and `login.html` links the light-cord scene:

```
TOTAL: 102  PASS: 102  FAIL: 0
```

**3 — UI smoke test** copies a tiny login harness into `frontend/`, then drives
headless Chrome through a **real login** into each page for each role —
**196 checks**. It fails on any JavaScript exception, a missing app shell, a
bounce back to the login page, a list page that rendered zero rows, or a
protected page that a role was not denied. Dumps land in `%TEMP%\sc-ui-dump\`
and the harness is removed again when the run finishes.

**4 — Login light & theme colour suite** drives the pull-the-cord switch on the
real `login.html` (with `tests/_lighttest.html` as its harness): the light
starts **OFF**, the first pull switches it on and fades the form in, the second
pull hides it again, typed credentials survive every toggle, a wrong password
still returns the existing error while the right one performs a real redirect —
plus the eleven theme swatches and the “+” colour input: apply, read-out,
`sc.theme.color` persistence across a reload, and “no password in
localStorage”. **55 checks**, and it raises **no JavaScript exceptions**:

```
TOTAL: 55  PASS: 55  FAIL: 0
```

---

## 9. Troubleshooting

| Symptom | Fix |
|---|---|
| `Public Access Denied` / `Access denied for user 'root'` | MySQL is not running, or the password in `application.properties` is wrong |
| `Unknown database 'smart_campus_db'` | Run `database/schema.sql` first |
| Port 8080 already in use | `netstat -ano \| findstr :8080`, then stop that process, or change `server.port` |
| Front end shows *"Cannot reach the server"* | The back end is not running — start it with `mvn spring-boot:run` |
| `mvn` not recognised | Add Maven's `bin` folder to the PATH, or use VS Code's integrated terminal |
| `java` not recognised | Install JDK 25 and set `JAVA_HOME`, then reopen the terminal |
| Blank dashboard charts | Check the browser console; the statistics call is `/api/dashboard/statistics` and needs a valid session |
| Login says *session expired* | Your token expired after 12 hours — sign in again |

---

## 10. Where to look for each requirement

See **`PROJECT_DOCUMENTATION.md`** for the full mapping of every OOP concept
(classes, constructors, encapsulation, inheritance, polymorphism, abstract
classes, interfaces, custom exceptions, Collections, lambdas, Streams, generics,
file handling, JDBC, overloading/overriding, annotations, multithreading) to the
exact file that demonstrates it — plus the complete REST API reference,
step-by-step test script and known limitations.
#   s m a r t - c a m p u s  
 