package com.skapt.app.servlet;

import com.skapt.app.config.Db;
import com.skapt.app.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseServlet extends HttpServlet {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            Db.initializeSchema();
        } catch (Exception ex) {
            logger.error("Schema initialization failed", ex);
            throw new ServletException("Database schema initialization failed", ex);
        }
    }

    protected String body(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = req.getReader()) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    protected void json(HttpServletResponse res, int status, Object payload) throws IOException {
        setCorsHeaders(res);
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write(JsonUtil.toJson(payload));
    }

    protected void error(HttpServletResponse res, int status, String message) throws IOException {
        setCorsHeaders(res);
        Map<String, Object> payload = new HashMap<>();
        payload.put("error", message == null ? "" : message);
        json(res, status, payload);
    }

    private void setCorsHeaders(HttpServletResponse res) {
        res.setHeader("Access-Control-Allow-Origin", "*");
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse res) throws IOException {
        setCorsHeaders(res);
        res.setStatus(HttpServletResponse.SC_OK);
    }

    protected boolean requireRole(HttpServletRequest req, HttpServletResponse res, String... roles) throws IOException {
        String role = String.valueOf(req.getAttribute("role"));
        for (String allowed : roles) {
            if (allowed.equalsIgnoreCase(role)) {
                return true;
            }
        }
        error(res, HttpServletResponse.SC_FORBIDDEN, "Forbidden: insufficient permissions");
        return false;
    }

    protected void logException(String context, Exception ex) {
        logger.error(context, ex);
    }
}
