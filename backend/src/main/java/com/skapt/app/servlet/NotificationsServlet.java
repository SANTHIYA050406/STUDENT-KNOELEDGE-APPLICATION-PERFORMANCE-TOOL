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

@WebServlet("/api/notifications/*")
public class NotificationsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        try {
            long userId = (Long) req.getAttribute("userId");
            boolean unreadOnly = "true".equalsIgnoreCase(req.getParameter("unreadOnly"));

            List<Map<String, Object>> notes = new ArrayList<>();
            String sql = "SELECT n.*, fu.username AS from_username, tu.username AS to_username " +
                "FROM notifications n " +
                "JOIN users fu ON fu.id=n.from_user_id " +
                "JOIN users tu ON tu.id=n.to_user_id " +
                "WHERE n.to_user_id=? " + (unreadOnly ? "AND n.read_at IS NULL " : "") +
                "ORDER BY n.id DESC";
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
                    row.put("readAt", ts(rs.getTimestamp("read_at")));
                    notes.add(row);
                }
            }

            json(res, HttpServletResponse.SC_OK, Map.of("notifications", notes));
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) {
        try {
            long fromUserId = (Long) req.getAttribute("userId");
            String fromRole = String.valueOf(req.getAttribute("role")).toLowerCase();

            Map<String, Object> payload = JsonUtil.fromJson(body(req), new TypeReference<>() {});
            String toUsername = str(payload.get("toUsername"));
            String message = str(payload.get("message"));
            if (toUsername.isBlank() || message.isBlank()) {
                error(res, HttpServletResponse.SC_BAD_REQUEST, "toUsername and message are required");
                return;
            }

            long toUserId = findUserIdByUsername(toUsername);
            if (toUserId <= 0) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "User not found");
                return;
            }

            if (!isConnected(fromUserId, fromRole, toUserId)) {
                error(res, HttpServletResponse.SC_FORBIDDEN, "Not connected");
                return;
            }

            long id;
            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO notifications (from_user_id, to_user_id, message) VALUES (?,?,?)",
                     PreparedStatement.RETURN_GENERATED_KEYS
                 )) {
                ps.setLong(1, fromUserId);
                ps.setLong(2, toUserId);
                ps.setString(3, message);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                id = keys.getLong(1);
            }

            json(res, HttpServletResponse.SC_CREATED, Map.of("id", id, "message", "Notification sent"));
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) {
        try {
            long userId = (Long) req.getAttribute("userId");
            String path = req.getPathInfo() == null ? "" : req.getPathInfo();
            String[] parts = path.split("/");
            if (parts.length < 3 || parts[1].isBlank() || !"read".equalsIgnoreCase(parts[2])) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Route not found");
                return;
            }

            long id = Long.parseLong(parts[1]);
            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE notifications SET read_at=COALESCE(read_at, CURRENT_TIMESTAMP) WHERE id=? AND to_user_id=?"
                 )) {
                ps.setLong(1, id);
                ps.setLong(2, userId);
                ps.executeUpdate();
            }
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    private boolean isConnected(long fromUserId, String fromRole, long toUserId) throws Exception {
        // Only allow student<->teacher/admin if an accepted group_request exists between them.
        if ("student".equals(fromRole)) {
            return hasAccepted(toUserId, fromUserId);
        }
        if ("teacher".equals(fromRole) || "admin".equals(fromRole)) {
            return hasAccepted(fromUserId, toUserId);
        }
        return false;
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

    private long findUserIdByUsername(String username) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username=? LIMIT 1")) {
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

