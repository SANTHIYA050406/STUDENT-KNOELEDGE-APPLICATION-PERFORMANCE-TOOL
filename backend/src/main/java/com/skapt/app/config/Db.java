package com.skapt.app.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

public final class Db {
    private static final DataSource DATA_SOURCE;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception ex) {
            throw new RuntimeException("MySQL driver load failed", ex);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(AppConfig.get("db.url"));
        config.setUsername(AppConfig.get("db.username"));
        config.setPassword(AppConfig.get("db.password"));
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
                uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_competition_user FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(users);
            st.execute(tests);
            st.execute(results);
            st.execute(academicMarksheets);
            st.execute(courseCertifications);
            st.execute(competitionCertificates);
        } catch (Exception ex) {
            throw new RuntimeException("Schema initialization failed", ex);
        }
    }
}
