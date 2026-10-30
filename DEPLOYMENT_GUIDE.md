# Smart Campus — Vercel Deployment Guide

This project is a Spring Boot + MySQL application with a static HTML/CSS/JS
frontend. Vercel hosts **the whole application** using its
[Container runtime](https://vercel.com/docs/functions/runtimes/container):
the `Dockerfile.vercel` image starts Spring Boot, and Spring Boot serves both
the REST API and the frontend from the same origin.

The only external service you need is a MySQL-compatible database. The current
deployment uses **TiDB Cloud Starter** (free, MySQL-compatible, no credit card).

```
Browser
   |
   v
https://your-project.vercel.app
   |
   +-- /              Spring Boot (Vercel container) -> redirects to /login.html
   +-- /api/**        Spring Boot REST API
   +-- /login.html    Static frontend files copied into the image
   |
   +-- MySQL over TLS -> TiDB Cloud Starter (port 4000)
```

> **Why not a static-only Vercel project?** The `/api` endpoints and the
> authentication tokens are handled by Spring Boot. A static-only deployment
> would need a separate backend host. The container deployment keeps the UI and
> API on one origin and avoids CORS/cookie problems.

---

## 1. Push the code to GitHub

The repository is already connected to:

```
https://github.com/r03981747-mp/college-pbl.git
```

From `D:\college pbl`:

```powershell
git status
git add .
git commit -m "Prepare full-stack Vercel deployment"
git push origin main
```

If `git push` asks you to authenticate, sign in to GitHub in the browser window
that opens.

---

## 2. Create the database (TiDB Cloud Starter)

TiDB Cloud Starter speaks the MySQL protocol, so the Spring Boot JDBC driver and
the existing SQL schema work without application-code changes.

### Option A — TiDB Cloud console (simplest)

1. Sign in at <https://tidbcloud.com/free-trial> with GitHub, Google, or email.
2. Create a **Starter** cluster. Choose a region near the Vercel region:
   - Vercel `iad1` (US East) → TiDB `N. Virginia (aws-us-east-1)`
   - Vercel `bom1` (Mumbai) → TiDB `Singapore (aws-ap-southeast-1)` is usually
     the closest available Starter region.
3. Wait until the cluster state is **Active**.
4. Open **SQL Users** and create a user with the **Read and Write** role.
   TiDB adds a prefix automatically, so the login name looks like
   `abcd1234.smartcampus`.
5. Copy the cluster's **public** host and port. The public endpoint normally
   uses port `4000` and requires TLS.
6. Open the cluster's SQL editor (or connect with the `mysql` client) and run:

   ```sql
   CREATE DATABASE IF NOT EXISTS smart_campus_db
     CHARACTER SET utf8mb4
     COLLATE utf8mb4_unicode_ci;
   ```

### Option B — `ticloud` CLI

Download the Windows ZIP from
<https://github.com/tidbcloud/tidbcloud-cli/releases>, extract
`ticloud.exe`, then run:

```powershell
# Opens a browser for OAuth device authorization.
.\ticloud.exe auth login

# Optional: list available Starter regions.
.\ticloud.exe serverless region

# Create the free Starter cluster. This waits until it is ready.
.\ticloud.exe serverless create `
  --display-name smart-campus `
  --region aws-us-east-1

# List clusters and copy the cluster ID.
.\ticloud.exe serverless list

# Create the application SQL user. Use a long, unique password.
.\ticloud.exe serverless sql-user create `
  --cluster-id <CLUSTER_ID> `
  --user smartcampus `
  --password '<STRONG_PASSWORD>' `
  --role role_readwrite

# Show the public host, port, user prefix, and cluster state.
.\ticloud.exe serverless describe -c <CLUSTER_ID>
```

The CLI output includes a `userPrefix`. The full MySQL login name is
`<userPrefix>.smartcampus`.

Create the database and load the schema with the `mysql` client:

```powershell
mysql --ssl-mode=REQUIRED `
  -h gateway01.us-east-1.prod.aws.tidbcloud.com `
  -P 4000 `
  -u 'abcd1234.smartcampus' `
  -p `
  -e "CREATE DATABASE IF NOT EXISTS smart_campus_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

TiDB Cloud Starter has a free monthly quota (currently 5 GiB of row storage and
50 million request units per cluster, with a spending limit of $0 by default).
The database scales to zero when idle, so the first request after a long pause
can be slower.

---

## 3. Load the schema and demo data

Use the **deployment** SQL files, not the local `schema.sql`. The deployment
files do not try to create or drop the database, so they work with a managed
MySQL user that only has permission on its own database.

```powershell
cd "D:\college pbl"

# Replace HOST, PREFIX.USER, and DATABASE with the values from TiDB.
# TiDB Cloud requires TLS on the public endpoint.
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

& $mysql --ssl-mode=REQUIRED -h HOST -P 4000 -u 'PREFIX.USER' -p smart_campus_db `
  < database/deploy_schema.sql

& $mysql --ssl-mode=REQUIRED -h HOST -P 4000 -u 'PREFIX.USER' -p smart_campus_db `
  < database/deploy_sample_data.sql
```

You can also paste the two deployment SQL files into the TiDB SQL editor and
run them in this order:

1. `database/deploy_schema.sql`
2. `database/deploy_sample_data.sql`

Verify the data:

```sql
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM students;
```

Demo accounts after loading the sample data:

| Role | Email | Password |
|---|---|---|
| Admin | `admin@smartcampus.com` | `Admin@123` |
| Faculty | `faculty@smartcampus.com` | `Faculty@123` |
| Student | `student@smartcampus.com` | `Student@123` |

> Change these passwords before sharing the URL publicly.

### Upgrading an existing database (marks, attendance, assignments, eligibility)

If the database already contains the original 11 tables and you are adding the
academic modules, do **not** reload `deploy_schema.sql` (it drops tables).

The recommended upgrade path is the application's automatic startup migration.
On startup it checks `information_schema` and adds only the missing academic
columns and tables. It uses direct DDL rather than `PREPARE`/`EXECUTE`, so it
works on both MySQL 8 and TiDB Cloud. It is enabled by default
(`APP_DB_ACADEMIC_MIGRATION_ENABLED=true`) and is idempotent, so restarts are
safe. Startup migration failures are logged without stopping the app.

It adds:

- `students.class_teacher_id` and `students.mentor_id`
- `academic_settings`, `assessments`, `student_marks`, `attendance`,
  `assignments`, and `assignment_submissions`

If you need to apply the schema before deploying, use the script for your
database:

- **MySQL 8:** `database/academic_migration.sql` is the idempotent manual
  migration (it uses `PREPARE`/`EXECUTE`).
- **TiDB Cloud:** rely on the automatic startup migration, or run the two
  `ALTER TABLE students` statements from `database/academic_migration.sql`
  followed by `backend/src/main/resources/db/academic_tables.sql`. The table
  script is plain `CREATE TABLE IF NOT EXISTS` and is TiDB-compatible.

After the schema exists, sign in as an administrator and open
**Academics → Academic Setup** to configure the eligibility thresholds and
assign class teachers/mentors. Sample academic records are optional; the demo
seed contains them, but real deployments should not load demo data.

### Assignment file storage

Assignment files are deliberately stored outside the web root and are only
served through authenticated endpoints such as
`/api/assignments/submissions/{id}/file`. Set `APP_UPLOAD_DIRECTORY` to a
persistent path in production. Vercel's container filesystem is ephemeral, so
`/tmp/uploads` works for a demo but files disappear when an instance is
replaced. Use a mounted volume or object storage for durable uploads.

---

## 4. Deploy to Vercel

### Option A — Vercel dashboard + Git integration

1. Go to <https://vercel.com/new> and import the GitHub repository.
2. Leave **Root Directory** as the repository root (`./`), not `frontend`.
   `Dockerfile.vercel` at the root builds the complete application.
3. Vercel should detect the **Container** framework from
   `Dockerfile.vercel`. If it does not, set **Framework Preset** to
   **Container**.
4. Leave **Build Command** and **Output Directory** empty.
5. Add these **Environment Variables** for Production, Preview, and
   Development:

   | Name | Value |
   |---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:mysql://gateway01.us-east-1.prod.aws.tidbcloud.com:4000/smart_campus_db?sslMode=REQUIRED&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Kolkata` |
   | `SPRING_DATASOURCE_USERNAME` | Full TiDB SQL user, including the prefix (for example `abcd1234.smartcampus`) |
   | `SPRING_DATASOURCE_PASSWORD` | The SQL user's password |
   | `APP_EXPORT_DIRECTORY` | `/tmp/exports` |
   | `APP_LOG_DIRECTORY` | `/tmp/logs` |
   | `DB_POOL_SIZE` | `3` |
   | `DB_MIN_IDLE` | `1` |

   Use the exact host, port, database, and user from **your** TiDB cluster.
   `sslMode=REQUIRED` is required for TiDB Cloud's public endpoint.
   `allowPublicKeyRetrieval=true` prevents a "Public Key Retrieval is not
   allowed" error.

6. Click **Deploy**. The first container build can take several minutes
   because Maven downloads the Spring Boot dependencies.
7. Open the production URL Vercel gives you. It should redirect to
   `/login.html`.

Every later `git push` to `main` redeploys automatically through the Git
integration.

### Option B — Vercel CLI

Install/run the CLI from the repository root:

```powershell
npx vercel@latest login
cd "D:\college pbl"
npx vercel@latest --prod --yes
```

The project is linked to `.vercel/project.json`. Add the environment variables
before the final production deployment. The easiest way is the Vercel
dashboard (Project → Settings → Environment Variables); a correctly configured
`.env.example` lists every required name.

After changing an environment variable, deploy again. Vercel does not apply
new environment variables to an existing deployment.

```powershell
npx vercel@latest --prod --yes
```

---

## 5. Verify the deployment

Replace `YOUR-URL` with the Vercel production URL:

```powershell
Invoke-RestMethod "https://YOUR-URL/api/health"
```

Expected result: a JSON response containing `"status": "UP"`.

Then check in a browser:

| Check | Expected result |
|---|---|
| `https://YOUR-URL/` | Redirects to `/login.html` |
| `https://YOUR-URL/login.html` | Login page loads with the light-cord animation |
| Admin login | Dashboard loads with students, courses, bookings, and charts |
| Students page | Data loads from the TiDB database |
| Create/update a record | Saves successfully |
| Reports page | CSV downloads successfully |

The browser calls `/api` on the same Vercel origin, so there is no frontend
API URL to configure and no CORS setup is required.

The current production deployment is:

```
https://smart-campus-lemon-mu.vercel.app
```

---

## 6. Local development (unchanged)

The container deployment does not change local development.

```powershell
# Terminal 1 — backend
cd "D:\college pbl\backend"
& "C:\Users\RAHULGOWDA M P\maven\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run

# Terminal 2 — frontend, optional
# Open frontend/login.html with VS Code Live Server.
```

The frontend automatically uses:

- `/api` when served by Spring Boot on port 8080 or by Vercel.
- `http://localhost:8080/api` when opened through Live Server on another port.

A host can also override the API URL before `api.js` loads:

```html
<script>
  window.SMART_CAMPUS_API_BASE = 'https://api.example.com/api';
</script>
```

---

## 7. Troubleshooting

| Problem | Fix |
|---|---|
| Build fails at `mvn package` | Check the Vercel build logs. The Dockerfile uses Java 17 and Maven 3.9.9; no local Maven wrapper is required. |
| App starts but login fails | Check `SPRING_DATASOURCE_URL`, user, password, and database name in Vercel. Test the same values with the `mysql` client. Remember that the TiDB username includes its prefix. |
| `Communications link failure` | Use TiDB's **public** host and port `4000`, not the private-link endpoint. Add `sslMode=REQUIRED` to the JDBC URL. |
| `Public Key Retrieval is not allowed` | Keep `allowPublicKeyRetrieval=true` in `SPRING_DATASOURCE_URL`. |
| `Access denied for user` | Use the full `<userPrefix>.username`. Reset the SQL user's password in the TiDB console or with `ticloud serverless sql-user`. |
| `Unknown database` | Create `smart_campus_db` first and use exactly that name in the JDBC URL. |
| `Table doesn't exist` | For a new database, run `database/deploy_schema.sql`, then `database/deploy_sample_data.sql`. For an existing database missing academic tables, run `database/academic_migration.sql` (or let the app's startup migration do it). |
| `SSL/TLS` handshake error | TiDB Cloud requires TLS on port 4000; keep `sslMode=REQUIRED` and do not use `useSSL=false`. |
| Report download fails | Confirm `APP_EXPORT_DIRECTORY=/tmp/exports`. Vercel's filesystem is read-only except `/tmp`. |
| First request is slow | Vercel container functions cold-start, and TiDB Starter can scale to zero. Wait a few seconds and retry. |
| Free quota exhausted | Check the TiDB Cloud console. Starter has a monthly free quota and a $0 default spending limit. |
| Environment change did nothing | Redeploy after changing Vercel environment variables. |
| Need to see runtime errors | Vercel → Project → Logs (or `npx vercel@latest logs`). |

---

## 8. How the deployment files fit together

| File | Purpose |
|---|---|
| `Dockerfile.vercel` | Root container image for Vercel: builds Spring Boot, copies the frontend, starts the app on `$PORT`. |
| `backend/Dockerfile` | Alternative standalone backend image for Railway or another container host. |
| `database/deploy_schema.sql` | Creates tables in an existing managed MySQL-compatible database. |
| `database/deploy_sample_data.sql` | Loads the demo users and records. |
| `database/academic_migration.sql` | Idempotent MySQL 8 upgrade for databases created before marks, attendance, assignments and eligibility existed. |
| `backend/src/main/resources/db/academic_tables.sql` | TiDB-compatible table DDL used by the automatic startup migration. |
| `.env.example` | Names and example values for the Vercel environment variables. |
| `.dockerignore`, `.vercelignore` | Keeps build artifacts, logs, and secrets out of deployment uploads. |

The Vercel container runtime runs the container only in response to requests.
There is no persistent local disk, so MySQL is used for all persistent state
and `/tmp` is used only for generated CSV/log files.
