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

    @Bean
    @Profile("prod")
    public DataSource dataSource(Environment env) throws Exception {
        HikariDataSource ds = new HikariDataSource();

        String jdbcUrl = resolveJdbcUrl(env);
        String username = resolveUsername(env);
        String password = resolvePassword(env);

        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);

        ds.setMaximumPoolSize(Integer.parseInt(env.getProperty("spring.datasource.hikari.maximum-pool-size", "10")));
        ds.setMinimumIdle(Integer.parseInt(env.getProperty("spring.datasource.hikari.minimum-idle", "1")));

        return ds;
    }

    private String resolveJdbcUrl(Environment env) throws Exception {
        String explicitJdbcUrl = firstNonBlank(
                env.getProperty("JDBC_DATABASE_URL"),
                env.getProperty("SPRING_DATASOURCE_URL")
        );
        if (explicitJdbcUrl != null) {
            return explicitJdbcUrl;
        }

        String databaseUrl = firstNonBlank(
                env.getProperty("DATABASE_URL"),
                env.getProperty("POSTGRES_URL"),
                env.getProperty("PGURL")
        );
        if (databaseUrl != null) {
            return toJdbcUrl(databaseUrl);
        }

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
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }

        throw new IllegalStateException("Missing PostgreSQL connection settings in prod profile");
    }

    private String resolveUsername(Environment env) {
        return firstNonBlank(
                env.getProperty("JDBC_DATABASE_USERNAME"),
                env.getProperty("DATABASE_USER"),
                env.getProperty("POSTGRES_USER"),
                env.getProperty("PGUSER"),
                env.getProperty("SPRING_DATASOURCE_USERNAME"),
                ""
        );
    }

    private String resolvePassword(Environment env) {
        return firstNonBlank(
                env.getProperty("JDBC_DATABASE_PASSWORD"),
                env.getProperty("DATABASE_PASSWORD"),
                env.getProperty("POSTGRES_PASSWORD"),
                env.getProperty("PGPASSWORD"),
                env.getProperty("SPRING_DATASOURCE_PASSWORD"),
                ""
        );
    }

    private String toJdbcUrl(String databaseUrl) throws Exception {
        if (databaseUrl.startsWith("jdbc:")) {
            return databaseUrl;
        }

        if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
            URI uri = new URI(databaseUrl);
            String userInfo = uri.getUserInfo();
            if (userInfo == null || userInfo.isBlank()) {
                return buildJdbcUrlFromHost(uri, null, null);
            }

            String[] userParts = userInfo.split(":", 2);
            String username = URLDecoder.decode(userParts[0], StandardCharsets.UTF_8);
            String password = userParts.length > 1 ? URLDecoder.decode(userParts[1], StandardCharsets.UTF_8) : "";
            return buildJdbcUrlFromHost(uri, username, password);
        }

        return databaseUrl;
    }

    private String buildJdbcUrlFromHost(URI uri, String username, String password) {
        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost())
                .append(":")
                .append(uri.getPort() > 0 ? uri.getPort() : 5432)
                .append(uri.getPath());

        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            jdbcUrl.append('?').append(uri.getQuery());
        }

        if (uri.getFragment() != null && !uri.getFragment().isBlank()) {
            jdbcUrl.append('#').append(uri.getFragment());
        }

        return jdbcUrl.toString();
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
