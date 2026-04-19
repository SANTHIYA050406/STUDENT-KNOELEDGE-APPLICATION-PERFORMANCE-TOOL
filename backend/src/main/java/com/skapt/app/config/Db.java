package com.skapt.app.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Db {
    private static final DataSource DATA_SOURCE;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception ex) {
            throw new RuntimeException("MySQL driver load failed", ex);
        }

        DbRuntime runtime = resolveRuntimeConfig();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(runtime.jdbcUrl);
        config.setUsername(runtime.username);
        config.setPassword(runtime.password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        DATA_SOURCE = new HikariDataSource(config);
    }

    private Db() {}

    public static Connection getConnection() throws Exception {
        return DATA_SOURCE.getConnection();
    }

    private static final class DbRuntime {
        final String jdbcUrl;
        final String username;
        final String password;

        DbRuntime(String jdbcUrl, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String toJdbcFromMysqlUrl(String mysqlUrl) {
        try {
            URI uri = new URI(mysqlUrl);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 3306 : uri.getPort();
            String db = uri.getPath();
            if (db == null) db = "";
            if (db.startsWith("/")) db = db.substring(1);
            if (host == null || host.isBlank() || db.isBlank()) {
                throw new IllegalArgumentException("Invalid MYSQL_URL (missing host/db)");
            }

            return "jdbc:mysql://" + host + ":" + port + "/" + db +
                "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        } catch (Exception ex) {
            throw new RuntimeException("Invalid MYSQL_URL format", ex);
        }
    }

    private static DbRuntime resolveRuntimeConfig() {
        // Railway MySQL provides MYSQL_URL and/or split variables like MYSQLHOST, MYSQLPORT, etc.
        // Our app uses JDBC, so convert when needed.
        String rawUrl = firstNonBlank(
            System.getenv("DB_URL"),
            System.getenv("MYSQL_URL"),
            System.getProperty("db.url"),
            AppConfig.getOptional("db.url")
        );

        String username = firstNonBlank(
            System.getenv("DB_USERNAME"),
            System.getenv("MYSQLUSER"),
            System.getProperty("db.username"),
            AppConfig.getOptional("db.username")
        );

        String password = firstNonBlank(
            System.getenv("DB_PASSWORD"),
            System.getenv("MYSQLPASSWORD"),
            System.getProperty("db.password"),
            AppConfig.getOptional("db.password")
        );

        String jdbcUrl;
        if (rawUrl == null || rawUrl.isBlank()) {
            String host = firstNonBlank(System.getenv("MYSQLHOST"), System.getenv("DB_HOST"));
            String port = firstNonBlank(System.getenv("MYSQLPORT"), System.getenv("DB_PORT"));
            String db = firstNonBlank(System.getenv("MYSQLDATABASE"), System.getenv("DB_NAME"));
            if (host == null || db == null) {
                throw new RuntimeException("Database config missing. Set DB_URL (jdbc:mysql://...) or Railway MySQL variables.");
            }
            String portVal = (port == null || port.isBlank()) ? "3306" : port.trim();
            jdbcUrl = "jdbc:mysql://" + host.trim() + ":" + portVal + "/" + db.trim() +
                "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        } else if (rawUrl.startsWith("jdbc:")) {
            jdbcUrl = rawUrl;
        } else if (rawUrl.startsWith("mysql://") || rawUrl.startsWith("mysqls://")) {
            // Railway MYSQL_URL is typically mysql://user:pass@host:port/db
            jdbcUrl = toJdbcFromMysqlUrl(rawUrl.replace("mysqls://", "mysql://"));

            // If username/password weren't explicitly provided, try extracting from the URL.
            try {
                URI uri = new URI(rawUrl.replace("mysqls://", "mysql://"));
                String userInfo = uri.getUserInfo(); // user:pass
                if (userInfo != null && !userInfo.isBlank()) {
                    String[] parts = userInfo.split(":", 2);
                    if ((username == null || username.isBlank()) && parts.length >= 1) {
                        username = parts[0];
                    }
                    if ((password == null || password.isBlank()) && parts.length == 2) {
                        password = parts[1];
                    }
                }
            } catch (Exception ignored) {}
        } else {
            // Assume user provided a JDBC-ish URL without the jdbc: prefix.
            jdbcUrl = rawUrl;
        }

        if (username == null || username.isBlank()) {
            throw new RuntimeException("Database username missing. Set DB_USERNAME or MYSQLUSER.");
        }
        if (password == null) {
            password = "";
        }

        // Helpful in Railway logs when diagnosing 503 (webapp fails to start).
        // Never print passwords.
        try {
            URI parsed = new URI(jdbcUrl.substring("jdbc:".length()));
            System.out.println("[db] jdbcUrlHost=" + parsed.getHost() + " jdbcUrlPort=" + (parsed.getPort() == -1 ? 3306 : parsed.getPort()));
        } catch (Exception ignored) {
            System.out.println("[db] jdbcUrl=" + jdbcUrl);
        }
        System.out.println("[db] username=" + username);

        return new DbRuntime(jdbcUrl, username, password);
    }

    public static void initializeSchema() {
        String users = """
            CREATE TABLE IF NOT EXISTS users (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(30) NOT NULL UNIQUE,
                password_hash VARCHAR(255) NOT NULL,
                role VARCHAR(10) NOT NULL,
                student_name VARCHAR(100),
                roll_no VARCHAR(50),
                department VARCHAR(100),
                year_of_study INT,
                cgpa DOUBLE,
                teacher_name VARCHAR(100),
                faculty_id VARCHAR(100),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;

        String tests = """
            CREATE TABLE IF NOT EXISTS tests (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                subject VARCHAR(100) NOT NULL,
                title VARCHAR(150) NOT NULL,
                duration INT NOT NULL DEFAULT 30,
                questions_json LONGTEXT NOT NULL,
                created_by BIGINT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_test_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        String results = """
            CREATE TABLE IF NOT EXISTS results (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                test_id BIGINT NOT NULL,
                student_id BIGINT NOT NULL,
                score INT NOT NULL,
                answers_json LONGTEXT NOT NULL,
                submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uq_test_student (test_id, student_id),
                CONSTRAINT fk_result_test FOREIGN KEY (test_id) REFERENCES tests(id) ON DELETE CASCADE,
                CONSTRAINT fk_result_user FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        String academicMarksheets = """
            CREATE TABLE IF NOT EXISTS academic_marksheets (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                student_id BIGINT NOT NULL,
                semester_no INT NOT NULL,
                file_name VARCHAR(255) NOT NULL,
                pdf_name VARCHAR(255) NOT NULL,
                pdf_data_url LONGTEXT NOT NULL,
                review_status VARCHAR(20) NOT NULL DEFAULT 'pending',
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_marksheet_user FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        String courseCertifications = """
            CREATE TABLE IF NOT EXISTS course_certifications (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                student_id BIGINT NOT NULL,
                cert_name VARCHAR(255) NOT NULL,
                cert_link TEXT,
                pdf_name VARCHAR(255) NOT NULL,
                pdf_data_url LONGTEXT NOT NULL,
                review_status VARCHAR(20) NOT NULL DEFAULT 'pending',
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_cert_user FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        String competitionCertificates = """
            CREATE TABLE IF NOT EXISTS competition_certificates (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                student_id BIGINT NOT NULL,
                competition_name VARCHAR(255) NOT NULL,
                competition_date DATE NOT NULL,
                online_link TEXT,
                pdf_name VARCHAR(255),
                pdf_data_url LONGTEXT,
                geo_proofs_json LONGTEXT NOT NULL,
                review_status VARCHAR(20) NOT NULL DEFAULT 'pending',
                uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_competition_user FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        String groupRequests = """
            CREATE TABLE IF NOT EXISTS group_requests (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                teacher_id BIGINT NOT NULL,
                student_id BIGINT NOT NULL,
                group_name VARCHAR(120) NOT NULL DEFAULT 'General',
                status VARCHAR(30) NOT NULL DEFAULT 'pending',
                requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                responded_at TIMESTAMP NULL,
                removed_at TIMESTAMP NULL,
                CONSTRAINT fk_gr_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
                CONSTRAINT fk_gr_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
                UNIQUE KEY uq_gr_teacher_student_group (teacher_id, student_id, group_name)
            )
        """;

        String teacherGroups = """
            CREATE TABLE IF NOT EXISTS teacher_groups (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                teacher_id BIGINT NOT NULL,
                group_name VARCHAR(120) NOT NULL,
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_tg_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
                UNIQUE KEY uq_teacher_group_name (teacher_id, group_name)
            )
        """;

        String teacherGroupMembers = """
            CREATE TABLE IF NOT EXISTS teacher_group_members (
                group_id BIGINT NOT NULL,
                student_id BIGINT NOT NULL,
                added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (group_id, student_id),
                CONSTRAINT fk_tgm_group FOREIGN KEY (group_id) REFERENCES teacher_groups(id) ON DELETE CASCADE,
                CONSTRAINT fk_tgm_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        String notifications = """
            CREATE TABLE IF NOT EXISTS notifications (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                from_user_id BIGINT NOT NULL,
                to_user_id BIGINT NOT NULL,
                message TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                read_at TIMESTAMP NULL,
                CONSTRAINT fk_note_from FOREIGN KEY (from_user_id) REFERENCES users(id) ON DELETE CASCADE,
                CONSTRAINT fk_note_to FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        String feedback = """
            CREATE TABLE IF NOT EXISTS feedback_messages (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                from_teacher_id BIGINT NOT NULL,
                to_student_id BIGINT NOT NULL,
                message TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_fb_teacher FOREIGN KEY (from_teacher_id) REFERENCES users(id) ON DELETE CASCADE,
                CONSTRAINT fk_fb_student FOREIGN KEY (to_student_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(users);
            // If the users table already existed from an earlier version, add missing columns.
            // (CREATE TABLE IF NOT EXISTS does not update existing schemas.)
            addColumnIfMissing(st, "users", "year_of_study", "INT");
            st.execute(tests);
            st.execute(results);
            st.execute(academicMarksheets);
            addColumnIfMissing(st, "academic_marksheets", "review_status", "VARCHAR(20) NOT NULL DEFAULT 'pending'");
            st.execute(courseCertifications);
            addColumnIfMissing(st, "course_certifications", "review_status", "VARCHAR(20) NOT NULL DEFAULT 'pending'");
            st.execute(competitionCertificates);
            addColumnIfMissing(st, "competition_certificates", "review_status", "VARCHAR(20) NOT NULL DEFAULT 'pending'");

            st.execute(groupRequests);
            st.execute(teacherGroups);
            st.execute(teacherGroupMembers);
            st.execute(notifications);
            st.execute(feedback);
        } catch (Exception ex) {
            throw new RuntimeException("Schema initialization failed", ex);
        }
    }

    private static void addColumnIfMissing(Statement st, String table, String column, String ddlType) throws Exception {
        try {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddlType);
        } catch (SQLException ex) {
            // MySQL duplicate column error code.
            if (ex.getErrorCode() == 1060) return;
            String msg = ex.getMessage();
            if (msg != null && msg.toLowerCase().contains("duplicate column")) return;
            throw ex;
        }
    }
}
