package com.skapt.app.servlet;

import com.skapt.app.config.Db;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/reports/*")
public class ReportsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        try {
            String path = req.getPathInfo() == null ? "" : req.getPathInfo();
            if ("/top-scorers".equals(path)) {
                renderTopScorers(req, res);
                return;
            }
            error(res, HttpServletResponse.SC_NOT_FOUND, "Route not found");
        } catch (Exception ex) {
            logException("Request handling failed", ex);
            try {
                error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            } catch (Exception ignored) {}
        }
    }

    private void renderTopScorers(HttpServletRequest req, HttpServletResponse res) throws Exception {
        String yearParam = req.getParameter("year");
        if (yearParam == null || yearParam.isBlank()) {
            error(res, HttpServletResponse.SC_BAD_REQUEST, "Year parameter is required");
            return;
        }
        int year;
        try {
            year = Integer.parseInt(yearParam);
        } catch (NumberFormatException ex) {
            error(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid year");
            return;
        }

        Long userId = (Long) req.getAttribute("userId");
        if (userId == null) {
            error(res, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT u.cgpa, IFNULL(cc.cert_count,0) AS cert_count, IFNULL(cp.competition_count,0) AS competition_count " +
                 "FROM users u " +
                 "LEFT JOIN (SELECT student_id, COUNT(*) AS cert_count FROM course_certifications GROUP BY student_id) cc ON cc.student_id = u.id " +
                 "LEFT JOIN (SELECT student_id, COUNT(*) AS competition_count FROM competition_certificates GROUP BY student_id) cp ON cp.student_id = u.id " +
                 "WHERE u.role='student' AND u.year_of_study = ?"
             )) {
            ps.setInt(1, year);
            ResultSet rs = ps.executeQuery();
            double topCgpa = 0;
            int topCert = 0;
            int topComp = 0;
            double topAchievement = 0;
            boolean hasData = false;
            while (rs.next()) {
                hasData = true;
                double cgpa = rs.getObject("cgpa") == null ? 0 : rs.getDouble("cgpa");
                int certCount = rs.getInt("cert_count");
                int competitionCount = rs.getInt("competition_count");
                double achievement = cgpa + certCount + competitionCount;
                topCgpa = Math.max(topCgpa, cgpa);
                topCert = Math.max(topCert, certCount);
                topComp = Math.max(topComp, competitionCount);
                topAchievement = Math.max(topAchievement, achievement);
            }

            Map<String, Object> metrics = hasData
                ? Map.of(
                    "cgpa", topCgpa,
                    "certCount", topCert,
                    "competitionCount", topComp,
                    "achievementScore", topAchievement
                )
                : null;
            Map<String, Object> response = new HashMap<>();
            response.put("year", year);
            response.put("topMetrics", metrics);
            json(res, HttpServletResponse.SC_OK, response);
        }
    }
}
