package com.skapt.app.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtil {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {}

    public static <T> T fromJson(String raw, Class<T> type) {
        try {
            return MAPPER.readValue(raw, type);
        } catch (Exception ex) {
            throw new RuntimeException("Invalid JSON payload", ex);
        }
    }

    public static <T> T fromJson(String raw, TypeReference<T> type) {
        try {
            return MAPPER.readValue(raw, type);
        } catch (Exception ex) {
            throw new RuntimeException("Invalid JSON payload", ex);
        }
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException("JSON serialization failed", ex);
        }
    }
}
