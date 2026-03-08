package com.skapt.app.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class Db {
    private static final String URL = AppConfig.get("db.url");
    private static final String USER = AppConfig.get("db.username");
    private static final String PASSWORD = AppConfig.get("db.password");

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception ex) {
            throw new RuntimeException("MySQL driver load failed", ex);
        }
    }

    private Db() {}

    public static Connection getConnection() throws Exception {
        return DriverManager.getConnection(URL, USER, PASSWORD);
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

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(users);
            st.execute(tests);
            st.execute(results);
        } catch (Exception ex) {
            throw new RuntimeException("Schema initialization failed", ex);
        }
    }
}
