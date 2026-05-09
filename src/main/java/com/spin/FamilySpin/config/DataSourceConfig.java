package com.spin.FamilySpin.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    private static class DbConfig {
        String jdbcUrl;
        String username;
        String password;

        DbConfig(String jdbcUrl, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }
    }

    @Bean
    @Profile("prod")
    public DataSource dataSource(Environment env) throws Exception {
        HikariDataSource ds = new HikariDataSource();

        DbConfig config = resolveDbConfig(env);
        ds.setJdbcUrl(config.jdbcUrl);
        ds.setUsername(config.username);
        ds.setPassword(config.password);

        ds.setMaximumPoolSize(Integer.parseInt(env.getProperty("spring.datasource.hikari.maximum-pool-size", "10")));
        ds.setMinimumIdle(Integer.parseInt(env.getProperty("spring.datasource.hikari.minimum-idle", "1")));

        return ds;
    }

    private DbConfig resolveDbConfig(Environment env) throws Exception {
        // First try explicit JDBC vars
        String explicitJdbcUrl = firstNonBlank(
                env.getProperty("JDBC_DATABASE_URL"),
                env.getProperty("SPRING_DATASOURCE_URL")
        );
        if (explicitJdbcUrl != null) {
            String username = firstNonBlank(
                    env.getProperty("JDBC_DATABASE_USERNAME"),
                    env.getProperty("DATABASE_USER"),
                    env.getProperty("POSTGRES_USER"),
                    ""
            );
            String password = firstNonBlank(
                    env.getProperty("JDBC_DATABASE_PASSWORD"),
                    env.getProperty("DATABASE_PASSWORD"),
                    env.getProperty("POSTGRES_PASSWORD"),
                    ""
            );
            return new DbConfig(explicitJdbcUrl, username, password);
        }

        // Try DATABASE_URL with embedded credentials
        String databaseUrl = firstNonBlank(
                env.getProperty("DATABASE_URL"),
                env.getProperty("POSTGRES_URL"),
                env.getProperty("PGURL")
        );
        if (databaseUrl != null) {
            return parseDatabaseUrl(databaseUrl);
        }

        // Try host/port/database separate vars
        String host = firstNonBlank(
                env.getProperty("DATABASE_HOST"),
                env.getProperty("POSTGRES_HOST"),
                env.getProperty("PGHOST")
        );
        String port = firstNonBlank(
                env.getProperty("DATABASE_PORT"),
                env.getProperty("POSTGRES_PORT"),
                env.getProperty("PGPORT"),
                "5432"
        );
        String database = firstNonBlank(
                env.getProperty("DATABASE_NAME"),
                env.getProperty("POSTGRES_DB"),
                env.getProperty("PGDATABASE")
        );

        if (host != null && database != null) {
            String username = firstNonBlank(
                    env.getProperty("DATABASE_USER"),
                    env.getProperty("POSTGRES_USER"),
                    env.getProperty("PGUSER"),
                    ""
            );
            String password = firstNonBlank(
                    env.getProperty("DATABASE_PASSWORD"),
                    env.getProperty("POSTGRES_PASSWORD"),
                    env.getProperty("PGPASSWORD"),
                    ""
            );
            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            return new DbConfig(jdbcUrl, username, password);
        }

        throw new IllegalStateException("Missing PostgreSQL connection settings in prod profile");
    }

    private DbConfig parseDatabaseUrl(String databaseUrl) throws Exception {
        if (databaseUrl.startsWith("jdbc:")) {
            return new DbConfig(databaseUrl, "", "");
        }

        if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
            URI uri = new URI(databaseUrl);
            String userInfo = uri.getUserInfo();
            String username = "";
            String password = "";

            if (userInfo != null && !userInfo.isBlank()) {
                String[] userParts = userInfo.split(":", 2);
                username = URLDecoder.decode(userParts[0], StandardCharsets.UTF_8);
                password = userParts.length > 1 ? URLDecoder.decode(userParts[1], StandardCharsets.UTF_8) : "";
            }

            // Build JDBC URL WITHOUT embedded credentials - credentials go in setUsername/setPassword
            String port = uri.getPort() > 0 ? String.valueOf(uri.getPort()) : "5432";
            StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                    .append(uri.getHost())
                    .append(":")
                    .append(port)
                    .append(uri.getPath());

            if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                jdbcUrl.append('?').append(uri.getQuery());
            }

            return new DbConfig(jdbcUrl.toString(), username, password);
        }

        return new DbConfig(databaseUrl, "", "");
    }


    @SafeVarargs
    private final String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
