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

@WebServlet("/api/results/*")
public class ResultsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        try {
            String role = String.valueOf(req.getAttribute("role"));
            long userId = (Long) req.getAttribute("userId");
            List<Map<String, Object>> results = new ArrayList<>();

            String sql = """
                SELECT r.*, t.title AS test_title, u.username AS student_username
                FROM results r
                JOIN tests t ON t.id = r.test_id
                JOIN users u ON u.id = r.student_id
            """;

            if ("student".equals(role)) {
                sql += " WHERE r.student_id = ? ";
            }
            sql += " ORDER BY r.id DESC";

            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if ("student".equals(role)) {
                    ps.setLong(1, userId);
                }
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    results.add(Map.of(
                        "id", rs.getLong("id"),
                        "testId", rs.getLong("test_id"),
                        "testTitle", rs.getString("test_title"),
                        "studentId", rs.getLong("student_id"),
                        "studentUsername", rs.getString("student_username"),
                        "score", rs.getInt("score"),
                        "answers", JsonUtil.fromJson(rs.getString("answers_json"), new TypeReference<List<String>>() {}),
                        "submittedAt", rs.getTimestamp("submitted_at").toInstant().toString()
                    ));
                }
            }

            json(res, HttpServletResponse.SC_OK, Map.of("results", results));
        } catch (Exception ex) {
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) {
        try {
            if (!requireRole(req, res, "student")) return;

            Map<String, Object> payload = JsonUtil.fromJson(body(req), new TypeReference<>() {});
            long testId = ((Number) payload.get("testId")).longValue();
            List<String> answers = (List<String>) payload.get("answers");
            long studentId = (Long) req.getAttribute("userId");

            Map<String, Object> test = getTest(testId);
            if (test == null) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Test not found");
                return;
            }

            List<Map<String, Object>> questions = (List<Map<String, Object>>) test.get("questions");
            int correct = 0;
            for (int i = 0; i < questions.size(); i++) {
                String selected = i < answers.size() && answers.get(i) != null ? answers.get(i).trim().toUpperCase() : "";
                String expected = String.valueOf(questions.get(i).get("answer")).trim().toUpperCase();
                if (selected.equals(expected)) {
                    correct++;
                }
            }
            int score = Math.round((correct * 100f) / questions.size());

            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO results (test_id, student_id, score, answers_json) VALUES (?,?,?,?) " +
                         "ON DUPLICATE KEY UPDATE score=VALUES(score), answers_json=VALUES(answers_json), submitted_at=CURRENT_TIMESTAMP"
                 )) {
                ps.setLong(1, testId);
                ps.setLong(2, studentId);
                ps.setInt(3, score);
                ps.setString(4, JsonUtil.toJson(answers));
                ps.executeUpdate();
            }

            try (Connection conn = Db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT r.*, t.title test_title, u.username student_username FROM results r " +
                         "JOIN tests t ON t.id=r.test_id JOIN users u ON u.id=r.student_id WHERE r.test_id=? AND r.student_id=?"
                 )) {
                ps.setLong(1, testId);
                ps.setLong(2, studentId);
                ResultSet rs = ps.executeQuery();
                rs.next();
                json(res, HttpServletResponse.SC_CREATED, Map.of("result", Map.of(
                    "id", rs.getLong("id"),
                    "testId", rs.getLong("test_id"),
                    "testTitle", rs.getString("test_title"),
                    "studentId", rs.getLong("student_id"),
                    "studentUsername", rs.getString("student_username"),
                    "score", rs.getInt("score"),
                    "answers", JsonUtil.fromJson(rs.getString("answers_json"), new TypeReference<List<String>>() {}),
                    "submittedAt", rs.getTimestamp("submitted_at").toInstant().toString()
                )));
            }
        } catch (Exception ex) {
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) {
        try {
            long resultId = Long.parseLong(req.getPathInfo().substring(1));
            long userId = (Long) req.getAttribute("userId");
            String role = String.valueOf(req.getAttribute("role"));

            try (Connection conn = Db.getConnection();
                 PreparedStatement check = conn.prepareStatement("SELECT student_id FROM results WHERE id=?")) {
                check.setLong(1, resultId);
                ResultSet rs = check.executeQuery();
                if (!rs.next()) {
                    error(res, HttpServletResponse.SC_NOT_FOUND, "Result not found");
                    return;
                }
                long studentId = rs.getLong("student_id");
                if (!"admin".equals(role) && studentId != userId) {
                    error(res, HttpServletResponse.SC_FORBIDDEN, "You cannot delete this result");
                    return;
                }
            }

            try (Connection conn = Db.getConnection();
                 PreparedStatement del = conn.prepareStatement("DELETE FROM results WHERE id=?")) {
                del.setLong(1, resultId);
                del.executeUpdate();
            }
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (Exception ex) {
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    private Map<String, Object> getTest(long id) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM tests WHERE id=?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            return Map.of(
                "id", rs.getLong("id"),
                "questions", JsonUtil.fromJson(rs.getString("questions_json"), new TypeReference<List<Map<String, Object>>>() {})
            );
        }
    }
}
