# Smart Campus — Vercel Deployment Guide

This project is a Spring Boot + MySQL application with a static HTML/CSS/JS
frontend. Vercel can host **the whole application** using its
[Container runtime](https://vercel.com/docs/functions/runtimes/container):
the `Dockerfile.vercel` image starts Spring Boot, and Spring Boot serves both
the REST API and the frontend from the same origin.

The only external service you need is a MySQL database. The recommended free
option is **Aiven for MySQL** (1 GB, no credit card).

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
   +-- MySQL over TLS -> Aiven / Railway / another managed MySQL host
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

## 2. Create the MySQL database

### Option A — Aiven free MySQL (recommended)

1. Sign in at <https://aiven.io/free-mysql-database>.
2. Create a **MySQL** service on the **Free** plan.
3. Choose a region close to the Vercel region you will use (for example,
   AWS `ap-south-1` / Mumbai).
4. Wait until the service status is **Running**.
5. On the service **Overview** page, copy:
   - Host
   - Port
   - Database (`defaultdb` by default)
   - User (`avnadmin` by default)
   - Password
6. Optional: download the CA certificate if you want strict TLS verification.

Aiven's free service may be powered off after a long period of inactivity.
Open the Aiven console and restart it before a demo if necessary.

### Option B — Railway MySQL

1. Create a Railway project and add a **MySQL** service.
2. Open the MySQL service's **Connect** tab and copy the public connection
   details (`MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`,
   `MYSQLPASSWORD`).
3. Use the public host/port (not `mysql.railway.internal`) because Vercel is
   outside Railway's private network.

---

## 3. Load the schema and demo data

Use the **deployment** SQL files, not the local `schema.sql`. The deployment
files do not try to create or drop the database, so they work with a managed
MySQL user that only has permission on its own database.

```powershell
cd "D:\college pbl"

# Replace HOST, PORT, USER, and DATABASE with the values from your provider.
# Add --ssl-mode=REQUIRED for Aiven (and for most public managed MySQL hosts).
mysql --ssl-mode=REQUIRED -h HOST -P PORT -u USER -p DATABASE `
  < database/deploy_schema.sql

mysql --ssl-mode=REQUIRED -h HOST -P PORT -u USER -p DATABASE `
  < database/deploy_sample_data.sql
```

If `mysql` is not on your PATH on Windows, use the full client path, for
example:

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" `
  --ssl-mode=REQUIRED -h HOST -P PORT -u USER -p DATABASE `
  < database/deploy_schema.sql
```

You can also paste the two deployment SQL files into your provider's SQL
console/query editor and run them in this order:

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

---

## 4. Deploy to Vercel

### Option A — Vercel dashboard + Git integration (recommended)

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
   | `SPRING_DATASOURCE_URL` | `jdbc:mysql://HOST:PORT/DATABASE?sslMode=REQUIRED&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Kolkata` |
   | `SPRING_DATASOURCE_USERNAME` | Your MySQL user (for example `avnadmin`) |
   | `SPRING_DATASOURCE_PASSWORD` | Your MySQL password |
   | `APP_EXPORT_DIRECTORY` | `/tmp/exports` |
   | `APP_LOG_DIRECTORY` | `/tmp/logs` |
   | `DB_POOL_SIZE` | `3` |
   | `DB_MIN_IDLE` | `1` |

   Use the exact host/port/database from your provider. If the provider gives
   you a ready-made JDBC/MySQL URL, make sure it starts with
   `jdbc:mysql://` and includes `sslMode=REQUIRED` when TLS is required.

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
npx vercel@latest --prod
```

The first run creates/links the Vercel project. Add the environment variables
before the final production deployment. The easiest way is the Vercel
dashboard (Project → Settings → Environment Variables); a correctly configured
`.env.example` lists every required name.

To deploy again later:

```powershell
npx vercel@latest --prod
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
| Students page | Data loads from the managed MySQL database |
| Create/update a record | Saves successfully |
| Reports page | CSV downloads successfully |

The browser calls `/api` on the same Vercel origin, so there is no frontend
API URL to configure and no CORS setup is required.

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
| App starts but login fails | Check `SPRING_DATASOURCE_URL`, user, password, and database name in Vercel. Test the same values with the `mysql` client. |
| `Communications link failure` | Use the provider's public host/port, not a private/internal address. Add `sslMode=REQUIRED` if the provider requires TLS. |
| `Unknown database` | Use the database name shown in the provider console (Aiven usually uses `defaultdb`). |
| `Table doesn't exist` | Run `database/deploy_schema.sql`, then `database/deploy_sample_data.sql`. |
| Report download fails | Confirm `APP_EXPORT_DIRECTORY=/tmp/exports`. Vercel's filesystem is read-only except `/tmp`. |
| First request is slow | Container functions cold-start. Wait a few seconds and retry. |
| Aiven database is offline | Aiven free services can power off after inactivity; restart the service in the Aiven console. |
| Need to see runtime errors | Vercel → Project → Logs (or `npx vercel@latest logs`). |

---

## 8. How the deployment files fit together

| File | Purpose |
|---|---|
| `Dockerfile.vercel` | Root container image for Vercel: builds Spring Boot, copies the frontend, starts the app on `$PORT`. |
| `backend/Dockerfile` | Alternative standalone backend image for Railway or another container host. |
| `database/deploy_schema.sql` | Creates tables in an existing managed database. |
| `database/deploy_sample_data.sql` | Loads the demo users and records. |
| `.env.example` | Names and example values for the Vercel environment variables. |
| `.dockerignore`, `.vercelignore` | Keeps build artifacts, logs, and secrets out of deployment uploads. |

The Vercel container runtime runs the container only in response to requests.
There is no persistent local disk, so MySQL is used for all persistent state
and `/tmp` is used only for generated CSV/log files.
