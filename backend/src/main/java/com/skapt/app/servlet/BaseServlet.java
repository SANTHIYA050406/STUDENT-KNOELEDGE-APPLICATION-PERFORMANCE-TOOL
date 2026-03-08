package com.skapt.app.servlet;

import com.skapt.app.util.JsonUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

public abstract class BaseServlet extends HttpServlet {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

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
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write(JsonUtil.toJson(payload));
    }

    protected void error(HttpServletResponse res, int status, String message) throws IOException {
        json(res, status, Map.of("error", message));
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
