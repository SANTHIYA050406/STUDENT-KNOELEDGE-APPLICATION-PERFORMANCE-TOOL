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

@WebServlet("/api/group-requests/*")
public class GroupRequestsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        try {
            long userId = (Long) req.getAttribute("userId");
            String role = String.valueOf(req.getAttribute("role")).toLowerCase();

            List<Map<String, Object>> requests = new ArrayList<>();
            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT gr.*, " +
                         "t.username AS teacher_username, t.teacher_name AS teacher_name, t.department AS teacher_department, " +
                         "s.username AS student_username, s.student_name AS student_name, s.roll_no AS student_roll_no " +
                         "FROM group_requests gr " +
                         "JOIN users t ON t.id = gr.teacher_id " +
                         "JOIN users s ON s.id = gr.student_id " +
                         "WHERE " + ("student".equals(role) ? "gr.student_id=?" : "gr.teacher_id=?") +
                         " ORDER BY gr.id DESC"
                 )) {
                ps.setLong(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    requests.add(mapRow(rs));
                }
            }

            json(res, HttpServletResponse.SC_OK, Map.of("requests", requests));
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
            String studentIdentifier = str(payload.get("studentIdentifier"));
            String groupName = str(payload.getOrDefault("groupName", "General"));
            if (groupName.isBlank()) groupName = "General";

            if (studentIdentifier.isBlank()) {
                error(res, HttpServletResponse.SC_BAD_REQUEST, "studentIdentifier is required");
                return;
            }

            long studentId = findStudentId(studentIdentifier);
            if (studentId <= 0) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Student not found");
                return;
            }

            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO group_requests (teacher_id, student_id, group_name, status) VALUES (?,?,?, 'pending')",
                     PreparedStatement.RETURN_GENERATED_KEYS
                 )) {
                ps.setLong(1, teacherId);
                ps.setLong(2, studentId);
                ps.setString(3, groupName);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                long id = keys.getLong(1);
                json(res, HttpServletResponse.SC_CREATED, Map.of("request", getById(id)));
            }
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            String msg = ex.getMessage();
            if (msg != null && msg.toLowerCase().contains("uq_gr_teacher_student_group")) {
                try { error(res, HttpServletResponse.SC_BAD_REQUEST, "Request already exists for that student and group"); } catch (Exception ignored) {}
                return;
            }
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) {
        try {
            long userId = (Long) req.getAttribute("userId");
            String role = String.valueOf(req.getAttribute("role")).toLowerCase();

            String path = req.getPathInfo();
            if (path == null || path.isBlank() || "/".equals(path)) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Route not found");
                return;
            }

            long id = Long.parseLong(path.substring(1));
            Map<String, Object> existing = getById(id);
            if (existing == null) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Request not found");
                return;
            }

            Map<String, Object> payload = JsonUtil.fromJson(body(req), new TypeReference<>() {});
            String status = str(payload.get("status")).toLowerCase();
            if (!List.of("accepted", "rejected", "removed_by_teacher", "pending").contains(status)) {
                error(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid status");
                return;
            }

            long teacherId = ((Number) existing.get("teacherId")).longValue();
            long studentId = ((Number) existing.get("studentId")).longValue();
            String currentStatus = String.valueOf(existing.get("status")).toLowerCase();

            if ("student".equals(role)) {
                if (studentId != userId) {
                    error(res, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                    return;
                }
                if (!"pending".equals(currentStatus)) {
                    error(res, HttpServletResponse.SC_BAD_REQUEST, "Only pending requests can be updated");
                    return;
                }
                if (!("accepted".equals(status) || "rejected".equals(status))) {
                    error(res, HttpServletResponse.SC_BAD_REQUEST, "Students can only accept or reject");
                    return;
                }
                updateStatus(id, status, Timestamp.from(java.time.Instant.now()), null);
            } else if ("teacher".equals(role) || "admin".equals(role)) {
                if (teacherId != userId && !"admin".equals(role)) {
                    error(res, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                    return;
                }
                if (!"removed_by_teacher".equals(status)) {
                    error(res, HttpServletResponse.SC_BAD_REQUEST, "Teachers can only remove via removed_by_teacher");
                    return;
                }
                updateStatus(id, status, null, Timestamp.from(java.time.Instant.now()));
            } else {
                error(res, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                return;
            }

            json(res, HttpServletResponse.SC_OK, Map.of("request", getById(id)));
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    private void updateStatus(long id, String status, Timestamp respondedAt, Timestamp removedAt) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE group_requests SET status=?, responded_at=COALESCE(?, responded_at), removed_at=COALESCE(?, removed_at) WHERE id=?"
             )) {
            ps.setString(1, status);
            ps.setTimestamp(2, respondedAt);
            ps.setTimestamp(3, removedAt);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    private long findStudentId(String identifier) throws Exception {
        String v = identifier.trim();
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM users WHERE role='student' AND (username=? OR roll_no=?) LIMIT 1"
             )) {
            ps.setString(1, v);
            ps.setString(2, v);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong("id") : -1L;
        }
    }

    private Map<String, Object> getById(long id) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT gr.*, " +
                     "t.username AS teacher_username, t.teacher_name AS teacher_name, t.department AS teacher_department, " +
                     "s.username AS student_username, s.student_name AS student_name, s.roll_no AS student_roll_no " +
                     "FROM group_requests gr " +
                     "JOIN users t ON t.id = gr.teacher_id " +
                     "JOIN users s ON s.id = gr.student_id " +
                     "WHERE gr.id=?"
             )) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapRow(rs) : null;
        }
    }

    private Map<String, Object> mapRow(ResultSet rs) throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("teacherId", rs.getLong("teacher_id"));
        row.put("teacherUsername", rs.getString("teacher_username"));
        row.put("teacherName", rs.getString("teacher_name"));
        row.put("teacherDepartment", rs.getString("teacher_department"));
        row.put("studentId", rs.getLong("student_id"));
        row.put("studentUsername", rs.getString("student_username"));
        row.put("studentName", rs.getString("student_name"));
        row.put("studentRollNo", rs.getString("student_roll_no"));
        row.put("groupName", rs.getString("group_name"));
        row.put("status", rs.getString("status"));
        row.put("requestedAt", ts(rs.getTimestamp("requested_at")));
        row.put("respondedAt", ts(rs.getTimestamp("responded_at")));
        row.put("removedAt", ts(rs.getTimestamp("removed_at")));
        return row;
    }

    private String ts(Timestamp ts) {
        return ts == null ? null : ts.toInstant().toString();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}

