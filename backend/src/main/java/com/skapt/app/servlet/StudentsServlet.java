package com.skapt.app.servlet;

import com.skapt.app.config.Db;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/users/students")
public class StudentsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        try {
            if (!requireRole(req, res, "teacher", "admin")) return;

            String rollNo = trim(req.getParameter("rollNo"));
            String username = trim(req.getParameter("username"));

            List<Map<String, Object>> students = new ArrayList<>();
            try (Connection conn = Db.getConnection()) {
                if (!rollNo.isBlank() || !username.isBlank()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM users WHERE role='student' AND (roll_no = ? OR username = ?) LIMIT 1"
                    )) {
                        ps.setString(1, rollNo);
                        ps.setString(2, username);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            students.add(mapStudentRow(rs));
                        }
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE role='student' ORDER BY id DESC");
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            students.add(mapStudentRow(rs));
                        }
                    }
                }
            }

            json(res, HttpServletResponse.SC_OK, Map.of("students", students));
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    private Map<String, Object> mapStudentRow(ResultSet rs) throws Exception {
        Map<String, Object> studentProfile = new HashMap<>();
        studentProfile.put("name", rs.getString("student_name"));
        studentProfile.put("rollNo", rs.getString("roll_no"));
        studentProfile.put("department", rs.getString("department"));
        studentProfile.put("cgpa", rs.getObject("cgpa"));

        Map<String, Object> student = new HashMap<>();
        student.put("id", rs.getLong("id"));
        student.put("username", rs.getString("username"));
        student.put("role", "student");
        student.put("studentProfile", studentProfile);
        student.put("teacherProfile", null);
        return student;
    }

    private String trim(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
