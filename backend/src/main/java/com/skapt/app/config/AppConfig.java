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
        String value = getOptional(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing config key: " + key);
        }
        return value;
    }

    public static String getOptional(String key) {
        // Prefer runtime overrides (Railway variables / JVM system props) over bundled defaults.
        // Example mappings:
        //   db.url -> DB_URL
        //   jwt.secret -> JWT_SECRET
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) {
            return sys;
        }

        String envKey = key.toUpperCase().replace('.', '_');
        String env = System.getenv(envKey);
        if (env == null || env.isBlank()) {
            env = System.getenv(key);
        }
        if (env != null && !env.isBlank()) {
            return env;
        }

        String fromProps = PROPS.getProperty(key);
        return (fromProps == null || fromProps.isBlank()) ? null : fromProps;
    }
}
