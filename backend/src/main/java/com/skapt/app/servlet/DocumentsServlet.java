package com.skapt.app.servlet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.skapt.app.config.Db;
import com.skapt.app.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/documents/*")
public class DocumentsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
        try {
            String type = normalizedType(req);
            if (type == null) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Document route not found");
                return;
            }

            String role = String.valueOf(req.getAttribute("role")).toLowerCase();
            long requesterId = (Long) req.getAttribute("userId");
            String studentUsername = trim(req.getParameter("studentUsername"));
            long studentId = resolveStudentId(role, requesterId, studentUsername);

            if ("academics".equals(type)) {
                listAcademics(studentId, res);
                return;
            }
            if ("certifications".equals(type)) {
                listCertifications(studentId, res);
                return;
            }
            listCompetitions(studentId, res);
        } catch (Exception ex) {
            logException("Document GET failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) {
        try {
            if (!requireRole(req, res, "student")) return;

            String type = normalizedType(req);
            if (type == null) {
                error(res, HttpServletResponse.SC_NOT_FOUND, "Document route not found");
                return;
            }

            long studentId = (Long) req.getAttribute("userId");
            Map<String, Object> payload = JsonUtil.fromJson(body(req), new TypeReference<>() {});

            if ("academics".equals(type)) {
                createAcademic(studentId, payload, res);
                return;
            }
            if ("certifications".equals(type)) {
                createCertification(studentId, payload, res);
                return;
            }
            createCompetition(studentId, payload, res);
        } catch (Exception ex) {
            logException("Document POST failed", ex);
            try { error(res, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage()); } catch (Exception ignored) {}
        }
    }

    private void listAcademics(long studentId, HttpServletResponse res) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, semester_no, file_name, pdf_name, pdf_data_url, updated_at FROM academic_marksheets WHERE student_id=? ORDER BY id DESC"
             )) {
            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("semesterNo", rs.getInt("semester_no"));
                row.put("fileName", rs.getString("file_name"));
                row.put("pdfProof", Map.of(
                    "name", rs.getString("pdf_name"),
                    "dataUrl", rs.getString("pdf_data_url")
                ));
                row.put("updatedAt", rs.getTimestamp("updated_at").toInstant().toString());
                rows.add(row);
            }
        }
        json(res, HttpServletResponse.SC_OK, Map.of("documents", rows));
    }

    private void listCertifications(long studentId, HttpServletResponse res) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, cert_name, cert_link, pdf_name, pdf_data_url, updated_at FROM course_certifications WHERE student_id=? ORDER BY id DESC"
             )) {
            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("certName", rs.getString("cert_name"));
                row.put("certLink", rs.getString("cert_link"));
                row.put("pdfProof", Map.of(
                    "name", rs.getString("pdf_name"),
                    "dataUrl", rs.getString("pdf_data_url")
                ));
                row.put("updatedAt", rs.getTimestamp("updated_at").toInstant().toString());
                rows.add(row);
            }
        }
        json(res, HttpServletResponse.SC_OK, Map.of("documents", rows));
    }

    private void listCompetitions(long studentId, HttpServletResponse res) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, competition_name, competition_date, online_link, pdf_name, pdf_data_url, geo_proofs_json, uploaded_at FROM competition_certificates WHERE student_id=? ORDER BY id DESC"
             )) {
            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("competitionName", rs.getString("competition_name"));
                row.put("competitionDate", rs.getDate("competition_date").toString());
                row.put("onlineLink", rs.getString("online_link"));
                String pdfName = rs.getString("pdf_name");
                String pdfDataUrl = rs.getString("pdf_data_url");
                row.put("pdfProof", (pdfName != null && pdfDataUrl != null) ? Map.of("name", pdfName, "dataUrl", pdfDataUrl) : null);
                row.put("geoProofs", JsonUtil.fromJson(rs.getString("geo_proofs_json"), new TypeReference<List<Map<String, Object>>>() {}));
                row.put("uploadedAt", rs.getTimestamp("uploaded_at").toInstant().toString());
                rows.add(row);
            }
        }
        json(res, HttpServletResponse.SC_OK, Map.of("documents", rows));
    }

    private void createAcademic(long studentId, Map<String, Object> payload, HttpServletResponse res) throws Exception {
        int semesterNo = intVal(payload.get("semesterNo"));
        String fileName = trim(payload.get("fileName"));
        Map<String, Object> pdfProof = mapVal(payload.get("pdfProof"));
        String pdfName = pdfProof == null ? "" : trim(pdfProof.get("name"));
        String pdfDataUrl = pdfProof == null ? "" : trim(pdfProof.get("dataUrl"));

        if (semesterNo < 1 || fileName.isBlank() || pdfName.isBlank() || pdfDataUrl.isBlank()) {
            throw new RuntimeException("semesterNo, fileName and pdfProof are required");
        }

        String rollNo = findRollNo(studentId);
        if (rollNo.isBlank()) {
            throw new RuntimeException("Student roll number not found in profile");
        }

        String expected = (rollNo + ".semester-" + semesterNo + " marksheet.pdf").toLowerCase();
        if (!fileName.toLowerCase().equals(expected)) {
            throw new RuntimeException("File name should be: " + rollNo + ".semester-" + semesterNo + " marksheet.pdf");
        }

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO academic_marksheets (student_id, semester_no, file_name, pdf_name, pdf_data_url) VALUES (?,?,?,?,?)"
             )) {
            ps.setLong(1, studentId);
            ps.setInt(2, semesterNo);
            ps.setString(3, fileName);
            ps.setString(4, pdfName);
            ps.setString(5, pdfDataUrl);
            ps.executeUpdate();
        }

        json(res, HttpServletResponse.SC_CREATED, Map.of("message", "Academic marksheet uploaded"));
    }

    private void createCertification(long studentId, Map<String, Object> payload, HttpServletResponse res) throws Exception {
        String certName = trim(payload.get("certName"));
        String certLink = trim(payload.get("certLink"));
        Map<String, Object> pdfProof = mapVal(payload.get("pdfProof"));
        String pdfName = pdfProof == null ? "" : trim(pdfProof.get("name"));
        String pdfDataUrl = pdfProof == null ? "" : trim(pdfProof.get("dataUrl"));

        if (certName.isBlank() || pdfName.isBlank() || pdfDataUrl.isBlank()) {
            throw new RuntimeException("certName and pdfProof are required");
        }

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO course_certifications (student_id, cert_name, cert_link, pdf_name, pdf_data_url) VALUES (?,?,?,?,?)"
             )) {
            ps.setLong(1, studentId);
            ps.setString(2, certName);
            ps.setString(3, certLink.isBlank() ? null : certLink);
            ps.setString(4, pdfName);
            ps.setString(5, pdfDataUrl);
            ps.executeUpdate();
        }

        json(res, HttpServletResponse.SC_CREATED, Map.of("message", "Certification uploaded"));
    }

    private void createCompetition(long studentId, Map<String, Object> payload, HttpServletResponse res) throws Exception {
        String competitionName = trim(payload.get("competitionName"));
        String competitionDate = trim(payload.get("competitionDate"));
        String onlineLink = trim(payload.get("onlineLink"));
        Map<String, Object> pdfProof = mapVal(payload.get("pdfProof"));
        List<Map<String, Object>> geoProofs = listMapVal(payload.get("geoProofs"));

        String pdfName = pdfProof == null ? "" : trim(pdfProof.get("name"));
        String pdfDataUrl = pdfProof == null ? "" : trim(pdfProof.get("dataUrl"));

        if (competitionName.isBlank() || competitionDate.isBlank()) {
            throw new RuntimeException("competitionName and competitionDate are required");
        }
        if (onlineLink.isBlank() && (pdfName.isBlank() || pdfDataUrl.isBlank())) {
            throw new RuntimeException("Online link or PDF proof is mandatory");
        }
        if (geoProofs == null || geoProofs.isEmpty()) {
            throw new RuntimeException("Geo tag photo proof is mandatory");
        }

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO competition_certificates (student_id, competition_name, competition_date, online_link, pdf_name, pdf_data_url, geo_proofs_json) VALUES (?,?,?,?,?,?,?)"
             )) {
            ps.setLong(1, studentId);
            ps.setString(2, competitionName);
            ps.setDate(3, Date.valueOf(competitionDate));
            ps.setString(4, onlineLink.isBlank() ? null : onlineLink);
            ps.setString(5, pdfName.isBlank() ? null : pdfName);
            ps.setString(6, pdfDataUrl.isBlank() ? null : pdfDataUrl);
            ps.setString(7, JsonUtil.toJson(geoProofs));
            ps.executeUpdate();
        }

        json(res, HttpServletResponse.SC_CREATED, Map.of("message", "Competition certificate uploaded"));
    }

    private long resolveStudentId(String role, long requesterId, String studentUsername) throws Exception {
        if ("student".equals(role)) {
            return requesterId;
        }
        if (!("teacher".equals(role) || "admin".equals(role))) {
            throw new RuntimeException("Forbidden");
        }
        if (studentUsername.isBlank()) {
            throw new RuntimeException("studentUsername is required");
        }
        long id = findUserIdByUsername(studentUsername);
        if (id <= 0) {
            throw new RuntimeException("Student not found");
        }
        return id;
    }

    private long findUserIdByUsername(String username) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username=? AND role='student'")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong("id") : -1L;
        }
    }

    private String findRollNo(long studentId) throws Exception {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT roll_no FROM users WHERE id=? AND role='student'")) {
            ps.setLong(1, studentId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? trim(rs.getString("roll_no")) : "";
        }
    }

    private String normalizedType(HttpServletRequest req) {
        String path = req.getPathInfo();
        if (path == null) return null;
        String type = path.replace("/", "").trim().toLowerCase();
        if ("academics".equals(type) || "certifications".equals(type) || "competition".equals(type)) {
            return type;
        }
        return null;
    }

    private String trim(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private int intVal(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ex) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapVal(Object v) {
        return v == null ? null : (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listMapVal(Object v) {
        return v == null ? null : (List<Map<String, Object>>) v;
    }
}
