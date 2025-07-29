# Webhook SQL Submitter

A small Spring Boot application that **generates a webhook + access token**, builds a **final SQL query**, and **submits that query** to the webhook using a **JWT Bearer token** — all **automatically on application startup** (no HTTP controllers exposed).

---

## What this application does

1. **Generate webhook + token**  
   On startup, the app sends a `POST` request to a remote endpoint to create a webhook and retrieve an `accessToken` (JWT).

2. **Pick the correct SQL**  
   The app prepares one of two SQL queries. Which one is chosen is determined by the **parity of the last two digits** of the configured registration number:
   - **Odd → Query 1 (Highest salary not on the 1st day of any month).**
   - **Even → Query 2 (For each employee, count how many colleagues in the same department are younger).**

3. **Submit the final SQL**  
   The app posts the chosen SQL as JSON (`{ "finalQuery": "<SQL here>" }`) to the returned webhook URL with header:  
   `Authorization: Bearer <accessToken>`

4. **Exit after completion (optional)**  
   If you run with `-Dexit.after.run=true`, the app performs the flow once and shuts down cleanly.

---

## Requirements

- **Java 17+** (JDK)  
- **Maven 3.9+**  
- Internet access to reach the API endpoints.

Check versions:
```bat
java -version
mvn -v
```

---

## Configuration

Edit `src/main/resources/application.properties` and set your details:

```properties
app.user.name=Your Name
app.user.email=you@example.com
app.user.regNo=REG12347
```

> The last two digits of `app.user.regNo` determine which SQL query is sent (odd → Query 1, even → Query 2).

---

## Build

```bat
mvn clean package
```

Artifacts:
- `target/webhook-sql-submitter-0.0.1-SNAPSHOT.jar`

Optionally copy a distributable JAR into `dist/`:

```bat
mkdir dist 2>nul
copy /Y target\webhook-sql-submitter-0.0.1-SNAPSHOT.jar dist\
```

The repository includes a prebuilt binary here:  
```
dist/webhook-sql-submitter-0.0.1-SNAPSHOT.jar
```

To download directly (raw link):
```
https://raw.githubusercontent.com/jasneet-arora27/webhook-sql-submitter/main/dist/webhook-sql-submitter-0.0.1-SNAPSHOT.jar
```

---

## Run

**Run once and exit after submission (recommended):**
```bat
java -Dexit.after.run=true -jar target/webhook-sql-submitter-0.0.1-SNAPSHOT.jar
```

**Or run normally (keeps the Spring context running):**
```bat
java -jar target/webhook-sql-submitter-0.0.1-SNAPSHOT.jar
```

Windows users can also double‑click `run.bat` (included).

---

## Endpoints used (at a glance)

- **Generate webhook** – `POST /hiring/generateWebhook/JAVA`  
  **Request JSON body** (sent by the app):  
  ```json
  {
    "name":  "<from app.user.name>",
    "regNo": "<from app.user.regNo>",
    "email": "<from app.user.email>"
  }
  ```
  **Response** includes:
  - `webhook` (string)
  - `accessToken` (JWT string)

- **Submit final SQL** – `POST <webhook>`  
  **Headers:** `Authorization: Bearer <accessToken>`  
  **Body:**  
  ```json
  { "finalQuery": "<your SQL here>" }
  ```

---

## SQL reference

### Query 1 — Highest salary credited **not** on the 1st day of any month

**Goal:** Return the highest salary (`SALARY`) that was not paid on day 1, along with employee `NAME`, `AGE`, and `DEPARTMENT_NAME`.

**MySQL‑compatible SQL:**
```sql
SELECT
  p.AMOUNT AS SALARY,
  CONCAT(e.FIRST_NAME, ' ', e.LAST_NAME) AS NAME,
  TIMESTAMPDIFF(YEAR, e.DOB, CURDATE()) AS AGE,
  d.DEPARTMENT_NAME
FROM PAYMENTS p
JOIN EMPLOYEE e ON p.EMP_ID = e.EMP_ID
JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID
WHERE DAY(p.PAYMENT_TIME) <> 1
ORDER BY p.AMOUNT DESC
LIMIT 1;
```

### Query 2 — For each employee, count colleagues in the same department who are **younger**

**Goal:** For every employee, return `EMP_ID`, `FIRST_NAME`, `LAST_NAME`, `DEPARTMENT_NAME`, and `YOUNGER_EMPLOYEES_COUNT` (number of employees in the same department whose age is less than theirs). Order by `EMP_ID` **descending**.

**MySQL‑compatible SQL (uses a self‑join):**
```sql
SELECT
  e1.EMP_ID,
  e1.FIRST_NAME,
  e1.LAST_NAME,
  d.DEPARTMENT_NAME,
  COUNT(e2.EMP_ID) AS YOUNGER_EMPLOYEES_COUNT
FROM EMPLOYEE e1
JOIN DEPARTMENT d
  ON e1.DEPARTMENT = d.DEPARTMENT_ID
LEFT JOIN EMPLOYEE e2
  ON e2.DEPARTMENT = e1.DEPARTMENT
 AND e2.DOB > e1.DOB      -- later DOB => younger
GROUP BY
  e1.EMP_ID,
  e1.FIRST_NAME,
  e1.LAST_NAME,
  d.DEPARTMENT_NAME
ORDER BY e1.EMP_ID DESC;
```

> Note: `DOB` comparison assumes a more recent date means younger. For engines that lack `TIMESTAMPDIFF`, the self‑join approach avoids computing ages explicitly.

---

## How it works (internals)

- The app uses Spring Boot and runs an `ApplicationRunner` (`StartupRunner`) so everything executes automatically on startup.  
- HTTP calls are performed with Spring’s `RestTemplate`.  
- The bearer token from the first call is applied to the second call’s `Authorization` header.  
- The final POST body **only** contains `"finalQuery"` as required by the receiving service.

---

## Logs (what you’ll see)

During a successful run you’ll see logs similar to:

```
=== Generating webhook & token ===
Webhook URL : https://.../hiring/testWebhook/JAVA
Access Token: <redacted>

=== Submitting final query ===
POST https://.../hiring/testWebhook/JAVA
Headers: [Content-Type: application/json, Authorization: <JWT>]
Body   : {finalQuery=SELECT ... }

=== Submission Result ===
200 OK
Body: {"success":true,"message":"Webhook processed successfully"}
```

---

## Troubleshooting

- **`401 Unauthorized` when submitting SQL**  
  - Ensure the `Authorization` header is `Bearer <accessToken>` (exact casing).  
  - Make sure you **do not** include extra fields in the JSON body; it should be exactly: `{ "finalQuery": "..." }`.  
  - The token may expire — re‑run the app to obtain a fresh token.

- **`400 Bad Request`**  
  - The JSON shape or content type is wrong; the app sends `application/json` automatically.  
  - Verify your SQL string is a single statement and properly escaped in JSON.

- **`408` or network errors**  
  - Retry; check connectivity, VPN/proxy, or firewall.  
  - If behind a corporate proxy, configure Git/Java/Maven proxy settings accordingly.

- **`invalid flag: --release` during build**  
  - You’re using an old JDK. Install **JDK 17+** and ensure `JAVA_HOME` and `PATH` point to it.  
  - Recheck with `java -version` and `mvn -v` (both should show Java 17+).

---

## Repository structure

```
.
├─ dist/
│  └─ webhook-sql-submitter-0.0.1-SNAPSHOT.jar     # optional distributable
├─ src/
│  ├─ main/
│  │  ├─ java/com/bajajfinserv/webhooksqlsubmitter/
│  │  │  ├─ StartupRunner.java
│  │  │  ├─ WebhookSqlSubmitterApplication.java
│  │  │  └─ model/ (request/response DTOs)
│  │  └─ resources/application.properties
│  └─ test/java/.../WebhookSqlSubmitterApplicationTests.java
├─ pom.xml
└─ run.bat
```

---

## Tech stack

- Java 17, Spring Boot 3.x
- RestTemplate (HTTP)
- Maven build

---