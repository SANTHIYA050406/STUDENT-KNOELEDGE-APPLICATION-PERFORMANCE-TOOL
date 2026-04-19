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

@WebServlet("/api/groups/*")
public class GroupsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        try {
            if (!requireRole(req, res, "teacher", "admin")) return;

            long teacherId = (Long) req.getAttribute("userId");
            List<Map<String, Object>> groups = listGroups(teacherId);
            json(res, HttpServletResponse.SC_OK, Map.of("groups", groups));
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
            String groupName = str(payload.get("groupName"));
            String description = str(payload.get("description"));
            List<?> members = (List<?>) payload.get("members");

            if (groupName.isBlank()) {
                error(res, HttpServletResponse.SC_BAD_REQUEST, "groupName is required");
                return;
            }
            if (members == null || members.isEmpty()) {
                error(res, HttpServletResponse.SC_BAD_REQUEST, "members is required");
                return;
            }

            List<Long> studentIds = new ArrayList<>();
            for (Object m : members) {
                String ident = str(m);
                long sid = findStudentId(ident);
                if (sid <= 0) {
                    error(res, HttpServletResponse.SC_BAD_REQUEST, "Student not found: " + ident);
                    return;
                }
                if (!hasAcceptedConnection(teacherId, sid)) {
                    error(res, HttpServletResponse.SC_BAD_REQUEST, "Student not accepted: " + ident);
                    return;
                }
                if (!studentIds.contains(sid)) studentIds.add(sid);
            }

            long groupId;
            try (Connection conn = Db.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO teacher_groups (teacher_id, group_name, description) VALUES (?,?,?)",
                    PreparedStatement.RETURN_GENERATED_KEYS
                )) {
                    ps.setLong(1, teacherId);
                    ps.setString(2, groupName);
                    ps.setString(3, description.isBlank() ? null : description);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    keys.next();
                    groupId = keys.getLong(1);
                }

                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO teacher_group_members (group_id, student_id) VALUES (?,?)"
                )) {
                    for (Long sid : studentIds) {
                        ps.setLong(1, groupId);
                        ps.setLong(2, sid);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            }

            Map<String, Object> created = getGroup(teacherId, groupId);
            json(res, HttpServletResponse.SC_CREATED, Map.of("group", created));
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            String msg = ex.getMessage();
            if (msg != null && msg.toLowerCase().contains("uq_teacher_group_name")) {
                try { error(res, HttpServletResponse.SC_BAD_REQUEST, "You already have a group with that name"); } catch (Exception ignored) {}
                return;
            }
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) {
        try {
            if (!requireRole(req, res, "teacher", "admin")) return;
            long teacherId = (Long) req.getAttribute("userId");

            String path = req.getPathInfo() == null ? "" : req.getPathInfo();
            String[] parts = path.split("/");
            if (parts.length < 2 || parts[1].isBlank()) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Route not found");
                return;
            }

            long groupId = Long.parseLong(parts[1]);
            if (parts.length >= 4 && "members".equalsIgnoreCase(parts[2])) {
                long studentId = Long.parseLong(parts[3]);
                removeMember(teacherId, groupId, studentId);
                res.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }

            deleteGroup(teacherId, groupId);
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    private List<Map<String, Object>> listGroups(long teacherId) throws Exception {
        List<Map<String, Object>> groups = new ArrayList<>();
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM teacher_groups WHERE teacher_id=? ORDER BY id DESC"
             )) {
            ps.setLong(1, teacherId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long id = rs.getLong("id");
                Map<String, Object> g = new HashMap<>();
                g.put("id", id);
                g.put("teacherId", rs.getLong("teacher_id"));
                g.put("groupName", rs.getString("group_name"));
                g.put("description", rs.getString("description"));
                g.put("createdAt", ts(rs.getTimestamp("created_at")));
                g.put("members", listMembers(id));
                groups.add(g);
            }
        }
        return groups;
    }

    private Map<String, Object> getGroup(long teacherId, long groupId) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM teacher_groups WHERE teacher_id=? AND id=?"
             )) {
            ps.setLong(1, teacherId);
            ps.setLong(2, groupId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            Map<String, Object> g = new HashMap<>();
            g.put("id", groupId);
            g.put("teacherId", rs.getLong("teacher_id"));
            g.put("groupName", rs.getString("group_name"));
            g.put("description", rs.getString("description"));
            g.put("createdAt", ts(rs.getTimestamp("created_at")));
            g.put("members", listMembers(groupId));
            return g;
        }
    }

    private List<Map<String, Object>> listMembers(long groupId) throws Exception {
        List<Map<String, Object>> members = new ArrayList<>();
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT u.* FROM teacher_group_members m JOIN users u ON u.id=m.student_id WHERE m.group_id=? ORDER BY u.id DESC"
             )) {
            ps.setLong(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(mapStudentUser(rs));
            }
        }
        return members;
    }

    private void deleteGroup(long teacherId, long groupId) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM teacher_groups WHERE teacher_id=? AND id=?")) {
            ps.setLong(1, teacherId);
            ps.setLong(2, groupId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("Group not found");
            }
        }
    }

    private void removeMember(long teacherId, long groupId, long studentId) throws Exception {
        // Ensure group belongs to teacher.
        try (Connection conn = Db.getConnection();
             PreparedStatement check = conn.prepareStatement("SELECT id FROM teacher_groups WHERE teacher_id=? AND id=?")) {
            check.setLong(1, teacherId);
            check.setLong(2, groupId);
            ResultSet rs = check.executeQuery();
            if (!rs.next()) {
                throw new RuntimeException("Group not found");
            }
        }

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM teacher_group_members WHERE group_id=? AND student_id=?")) {
            ps.setLong(1, groupId);
            ps.setLong(2, studentId);
            ps.executeUpdate();
        }
    }

    private boolean hasAcceptedConnection(long teacherId, long studentId) throws Exception {
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

    private Map<String, Object> mapStudentUser(ResultSet rs) throws Exception {
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", rs.getString("student_name"));
        profile.put("rollNo", rs.getString("roll_no"));
        profile.put("department", rs.getString("department"));
        profile.put("cgpa", rs.getObject("cgpa"));
        profile.put("yearOfStudy", rs.getObject("year_of_study"));

        Map<String, Object> user = new HashMap<>();
        user.put("id", rs.getLong("id"));
        user.put("username", rs.getString("username"));
        user.put("role", "student");
        user.put("studentProfile", profile);
        user.put("teacherProfile", null);
        return user;
    }

    private String ts(Timestamp ts) {
        return ts == null ? null : ts.toInstant().toString();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}

