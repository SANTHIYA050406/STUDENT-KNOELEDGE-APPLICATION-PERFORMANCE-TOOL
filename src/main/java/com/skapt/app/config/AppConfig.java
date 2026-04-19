package com.skapt.app.config;

import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (in == null) {
                throw new IllegalStateException("app.properties not found");
            }
            PROPS.load(in);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load app.properties", ex);
        }
    }

    private AppConfig() {}

    public static String get(String key) {
        String value = PROPS.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing config key: " + key);
        }
        return value;
    }
}
