package com.skapt.app.servlet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.skapt.app.config.Db;
import com.skapt.app.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/feedback/*")
public class FeedbackServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        try {
            long userId = (Long) req.getAttribute("userId");
            String role = String.valueOf(req.getAttribute("role")).toLowerCase();

            List<Map<String, Object>> items = new ArrayList<>();
            String sql;
            if ("student".equals(role)) {
                sql = "SELECT f.*, tu.username AS to_username, fu.username AS from_username " +
                    "FROM feedback_messages f " +
                    "JOIN users fu ON fu.id=f.from_teacher_id " +
                    "JOIN users tu ON tu.id=f.to_student_id " +
                    "WHERE f.to_student_id=? ORDER BY f.id DESC";
            } else if ("teacher".equals(role) || "admin".equals(role)) {
                sql = "SELECT f.*, tu.username AS to_username, fu.username AS from_username " +
                    "FROM feedback_messages f " +
                    "JOIN users fu ON fu.id=f.from_teacher_id " +
                    "JOIN users tu ON tu.id=f.to_student_id " +
                    "WHERE f.from_teacher_id=? ORDER BY f.id DESC";
            } else {
                error(res, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                return;
            }

            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("fromUsername", rs.getString("from_username"));
                    row.put("toUsername", rs.getString("to_username"));
                    row.put("message", rs.getString("message"));
                    row.put("createdAt", ts(rs.getTimestamp("created_at")));
                    items.add(row);
                }
            }

            json(res, HttpServletResponse.SC_OK, Map.of("feedback", items));
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) {
        try {
            if (!requireRole(req, res, "teacher", "admin")) return;

            long teacherId = (Long) req.getAttribute("userId");
            Map<String, Object> payload = JsonUtil.fromJson(body(req), new TypeReference<>() {});
            String toUsername = str(payload.get("toUsername"));
            String message = str(payload.get("message"));

            if (toUsername.isBlank() || message.isBlank()) {
                error(res, HttpServletResponse.SC_BAD_REQUEST, "toUsername and message are required");
                return;
            }

            long studentId = findStudentIdByUsername(toUsername);
            if (studentId <= 0) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Student not found");
                return;
            }

            if (!hasAccepted(teacherId, studentId)) {
                error(res, HttpServletResponse.SC_FORBIDDEN, "Not connected");
                return;
            }

            long id;
            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO feedback_messages (from_teacher_id, to_student_id, message) VALUES (?,?,?)",
                     PreparedStatement.RETURN_GENERATED_KEYS
                 )) {
                ps.setLong(1, teacherId);
                ps.setLong(2, studentId);
                ps.setString(3, message);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                id = keys.getLong(1);
            }

            json(res, HttpServletResponse.SC_CREATED, Map.of("id", id, "message", "Feedback sent"));
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    private boolean hasAccepted(long teacherId, long studentId) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM group_requests WHERE teacher_id=? AND student_id=? AND status='accepted' LIMIT 1"
             )) {
            ps.setLong(1, teacherId);
            ps.setLong(2, studentId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    private long findStudentIdByUsername(String username) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE role='student' AND username=? LIMIT 1")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong("id") : -1L;
        }
    }

    private String ts(Timestamp ts) {
        return ts == null ? null : ts.toInstant().toString();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}

