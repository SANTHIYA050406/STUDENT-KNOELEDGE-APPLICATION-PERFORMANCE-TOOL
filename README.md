# Student Knowledge Application Performance Tool

## Stack
- Backend: Java Servlet API + JDBC + MySQL + JWT
- Frontend: HTML, CSS, JavaScript

## Backend Setup (Plain Java + MySQL)
1. Ensure MySQL is running.
2. Update `backend/src/main/resources/app.properties`:
   - `db.username`
   - `db.password`
   - `jwt.secret` (minimum 32 chars)
3. Run backend:
   - `cd backend`
   - `mvn jetty:run`
4. API base URL: `http://localhost:8080/api`

## Frontend Setup
1. Serve project root with any static server (Live Server is fine).
2. Open `index.html`.

## Implemented Requirements
- RESTful API routes for auth/tests/results/users/documents.
- Database integration using JDBC with MySQL schema auto-init.
- JWT authentication and role-based authorization via servlet filter.
- Full-stack CRUD:
  - Tests create/read/update/delete from teacher UI (`create_test.html`)
  - Results create/read/delete from student UI (`take_test.html`, `dashboard.html`)
  - Student documents upload/read (academics, certifications, competition) from UI and teacher detail view
- Client-side state management in `js/store.js`.
- Error handling and security:
  - Input checks in servlet handlers.
  - Security headers and CORS in `AuthFilter`.
  - Basic backend logging through servlet and filter logs.

## API Testing
- Postman collection: `postman/skapt-java-api.postman_collection.json`
- Import and set `{{token}}` from login response.

## Viva-Friendly Architecture
- `AuthServlet`: login/register/me
- `TestsServlet`: test CRUD
- `ResultsServlet`: submit/list/delete results
- `StudentsServlet`: teacher/admin student listing
- `DocumentsServlet`: academics/certifications/competition upload + listing
- `AuthFilter`: token verification and secure headers
- `Db`: MySQL connection + table creation
