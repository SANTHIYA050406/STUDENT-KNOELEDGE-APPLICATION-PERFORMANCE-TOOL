package com.skapt.app.servlet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.skapt.app.config.Db;
import com.skapt.app.security.JwtUtil;
import com.skapt.app.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/auth/*")
public class AuthServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) {
        try {
            String path = req.getPathInfo() == null ? "" : req.getPathInfo();
            if ("/register".equals(path)) {
                register(req, res);
                return;
            }
            if ("/login".equals(path)) {
                login(req, res);
                return;
            }
            error(res, HttpServletResponse.SC_NOT_FOUND, "Route not found");
        } catch (Exception ex) {
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        try {
            String path = req.getPathInfo() == null ? "" : req.getPathInfo();
            if (!"/me".equals(path)) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Route not found");
                return;
            }

            Long userId = (Long) req.getAttribute("userId");
            Map<String, Object> user = findUserById(userId);
            if (user == null) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "User not found");
                return;
            }
            json(res, HttpServletResponse.SC_OK, Map.of("user", user));
        } catch (Exception ex) {
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    private void register(HttpServletRequest req, HttpServletResponse res) throws Exception {
        Map<String, Object> payload = JsonUtil.fromJson(body(req), new TypeReference<>() {});
        String username = str(payload.get("username"));
        String password = str(payload.get("password"));
        String role = str(payload.get("role")).toLowerCase();

        if (username.length() < 3 || password.length() < 6 || !(role.equals("student") || role.equals("teacher") || role.equals("admin"))) {
            throw new RuntimeException("Invalid register payload");
        }

        Map<String, Object> studentProfile = map(payload.get("studentProfile"));
        Map<String, Object> teacherProfile = map(payload.get("teacherProfile"));

        try (Connection conn = Db.getConnection()) {
            try (PreparedStatement check = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                check.setString(1, username);
                ResultSet rs = check.executeQuery();
                if (rs.next()) {
                    throw new RuntimeException("Username already exists");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (username,password_hash,role,student_name,roll_no,department,cgpa,teacher_name,faculty_id) VALUES (?,?,?,?,?,?,?,?,?)",
                PreparedStatement.RETURN_GENERATED_KEYS
            )) {
                ps.setString(1, username);
                ps.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
                ps.setString(3, role);
                ps.setString(4, studentProfile == null ? null : strNull(studentProfile.get("name")));
                ps.setString(5, studentProfile == null ? null : strNull(studentProfile.get("rollNo")));
                ps.setString(6, studentProfile != null ? strNull(studentProfile.get("department")) : (teacherProfile == null ? null : strNull(teacherProfile.get("department"))));
                ps.setObject(7, studentProfile == null ? null : numNull(studentProfile.get("cgpa")));
                ps.setString(8, teacherProfile == null ? null : strNull(teacherProfile.get("name")));
                ps.setString(9, teacherProfile == null ? null : strNull(teacherProfile.get("facultyId")));
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                long userId = keys.getLong(1);
                Map<String, Object> user = findUserById(userId);
                String token = JwtUtil.generateToken(userId, username, role);
                json(res, HttpServletResponse.SC_CREATED, Map.of("token", token, "user", user));
            }
        }
    }

    private void login(HttpServletRequest req, HttpServletResponse res) throws Exception {
        Map<String, Object> payload = JsonUtil.fromJson(body(req), new TypeReference<>() {});
        String username = str(payload.get("username"));
        String password = str(payload.get("password"));

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new RuntimeException("Invalid credentials");
            }

            String hash = rs.getString("password_hash");
            if (!BCrypt.checkpw(password, hash)) {
                throw new RuntimeException("Invalid credentials");
            }

            long userId = rs.getLong("id");
            String role = rs.getString("role");
            String token = JwtUtil.generateToken(userId, username, role);
            Map<String, Object> user = mapUserRow(rs);
            json(res, HttpServletResponse.SC_OK, Map.of("token", token, "user", user));
        }
    }

    private Map<String, Object> findUserById(long userId) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return mapUserRow(rs);
        }
    }

    private Map<String, Object> mapUserRow(ResultSet rs) throws Exception {
        Map<String, Object> user = new HashMap<>();
        String role = rs.getString("role").toLowerCase();
        user.put("id", rs.getLong("id"));
        user.put("username", rs.getString("username"));
        user.put("role", role);

        if ("student".equals(role)) {
            user.put("studentProfile", Map.of(
                "name", rs.getString("student_name"),
                "rollNo", rs.getString("roll_no"),
                "department", rs.getString("department"),
                "cgpa", rs.getObject("cgpa")
            ));
            user.put("teacherProfile", null);
        } else {
            user.put("studentProfile", null);
            user.put("teacherProfile", Map.of(
                "name", rs.getString("teacher_name"),
                "facultyId", rs.getString("faculty_id"),
                "department", rs.getString("department")
            ));
        }

        return user;
    }

    private String str(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
    private String strNull(Object v) { String s = str(v); return s.isBlank() ? null : s; }
    private Double numNull(Object v) { if (v == null) return null; try { return Double.parseDouble(String.valueOf(v)); } catch (Exception ex) { return null; } }
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object v) { return v == null ? null : (Map<String, Object>) v; }
}
