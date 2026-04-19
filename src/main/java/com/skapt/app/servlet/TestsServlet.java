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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WebServlet("/api/tests/*")
public class TestsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        try {
            String path = req.getPathInfo();
            if (path == null || "/".equals(path)) {
                listTests(res);
                return;
            }
            long id = Long.parseLong(path.substring(1));
            getTest(id, res);
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) {
        try {
            if (!requireRole(req, res, "teacher", "admin")) return;
            Map<String, Object> p = JsonUtil.fromJson(body(req), new TypeReference<>() {});

            String subject = String.valueOf(p.getOrDefault("subject", "")).trim();
            String title = String.valueOf(p.getOrDefault("title", "")).trim();
            int duration = ((Number) p.getOrDefault("duration", 30)).intValue();
            List<?> questions = (List<?>) p.get("questions");

            if (subject.isBlank() || title.isBlank() || questions == null || questions.isEmpty()) {
                throw new RuntimeException("subject, title and questions are required");
            }

            long userId = (Long) req.getAttribute("userId");
            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO tests (subject,title,duration,questions_json,created_by) VALUES (?,?,?,?,?)",
                     PreparedStatement.RETURN_GENERATED_KEYS
                 )) {
                ps.setString(1, subject);
                ps.setString(2, title);
                ps.setInt(3, duration);
                ps.setString(4, JsonUtil.toJson(questions));
                ps.setLong(5, userId);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                getTest(keys.getLong(1), res, HttpServletResponse.SC_CREATED);
            }
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) {
        try {
            if (!requireRole(req, res, "teacher", "admin")) return;
            long id = Long.parseLong(req.getPathInfo().substring(1));
            Map<String, Object> existing = getTestRaw(id);
            if (existing == null) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Test not found");
                return;
            }

            long userId = (Long) req.getAttribute("userId");
            String role = String.valueOf(req.getAttribute("role"));
            long createdBy = ((Number) existing.get("createdBy")).longValue();
            if (!"admin".equals(role) && createdBy != userId) {
                error(res, HttpServletResponse.SC_FORBIDDEN, "You can only update your tests");
                return;
            }

            Map<String, Object> p = JsonUtil.fromJson(body(req), new TypeReference<>() {});
            List<?> questions = (List<?>) p.get("questions");

            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE tests SET subject=?, title=?, duration=?, questions_json=? WHERE id=?")) {
                ps.setString(1, String.valueOf(p.get("subject")));
                ps.setString(2, String.valueOf(p.get("title")));
                ps.setInt(3, ((Number) p.getOrDefault("duration", 30)).intValue());
                ps.setString(4, JsonUtil.toJson(questions));
                ps.setLong(5, id);
                ps.executeUpdate();
            }
            getTest(id, res);
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) {
        try {
            if (!requireRole(req, res, "teacher", "admin")) return;
            long id = Long.parseLong(req.getPathInfo().substring(1));
            Map<String, Object> existing = getTestRaw(id);
            if (existing == null) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Test not found");
                return;
            }

            long userId = (Long) req.getAttribute("userId");
            String role = String.valueOf(req.getAttribute("role"));
            long createdBy = ((Number) existing.get("createdBy")).longValue();
            if (!"admin".equals(role) && createdBy != userId) {
                error(res, HttpServletResponse.SC_FORBIDDEN, "You can only delete your tests");
                return;
            }

            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM tests WHERE id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    private void listTests(HttpServletResponse res) throws Exception {
        List<Map<String, Object>> tests = new ArrayList<>();
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT t.*, u.username created_by_username FROM tests t JOIN users u ON u.id=t.created_by ORDER BY t.id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tests.add(mapTest(rs));
            }
        }
        json(res, HttpServletResponse.SC_OK, Map.of("tests", tests));
    }

    private void getTest(long id, HttpServletResponse res) throws Exception { getTest(id, res, HttpServletResponse.SC_OK); }

    private void getTest(long id, HttpServletResponse res, int status) throws Exception {
        Map<String, Object> test = getTestRaw(id);
        if (test == null) {
            error(res, HttpServletResponse.SC_NOT_FOUND, "Test not found");
            return;
        }
        json(res, status, Map.of("test", test));
    }

    private Map<String, Object> getTestRaw(long id) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT t.*, u.username created_by_username FROM tests t JOIN users u ON u.id=t.created_by WHERE t.id=?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            return mapTest(rs);
        }
    }

    private Map<String, Object> mapTest(ResultSet rs) throws Exception {
        return Map.of(
            "id", rs.getLong("id"),
            "subject", rs.getString("subject"),
            "title", rs.getString("title"),
            "duration", rs.getInt("duration"),
            "createdBy", rs.getLong("created_by"),
            "createdByUsername", rs.getString("created_by_username"),
            "createdAt", rs.getTimestamp("created_at").toInstant().toString(),
            "questions", JsonUtil.fromJson(rs.getString("questions_json"), new TypeReference<List<Map<String, Object>>>() {})
        );
    }
}


