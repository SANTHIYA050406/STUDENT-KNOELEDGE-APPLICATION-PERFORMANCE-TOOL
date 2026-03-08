package com.skapt.app.servlet;

import com.skapt.app.config.Db;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WebServlet("/api/users/students")
public class StudentsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        try {
            if (!requireRole(req, res, "teacher", "admin")) return;

            List<Map<String, Object>> students = new ArrayList<>();
            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE role='student' ORDER BY id DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    students.add(Map.of(
                        "id", rs.getLong("id"),
                        "username", rs.getString("username"),
                        "role", "student",
                        "studentProfile", Map.of(
                            "name", rs.getString("student_name"),
                            "rollNo", rs.getString("roll_no"),
                            "department", rs.getString("department"),
                            "cgpa", rs.getObject("cgpa")
                        ),
                        "teacherProfile", (Object) null
                    ));
                }
            }

            json(res, HttpServletResponse.SC_OK, Map.of("students", students));
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }
}


