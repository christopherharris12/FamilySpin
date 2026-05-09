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

@Configuration
public class DataSourceConfig {

    @Bean
    @Profile("prod")
    public DataSource dataSource(Environment env) throws Exception {
        // Support Render's DATABASE_URL (postgres://user:pass@host:port/db)
        String dbUrl = env.getProperty("JDBC_DATABASE_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = env.getProperty("DATABASE_URL");
        }

        if (dbUrl == null || dbUrl.isBlank()) {
            throw new IllegalStateException("Missing DATABASE_URL or JDBC_DATABASE_URL in prod profile");
        }

        HikariDataSource ds = new HikariDataSource();

        if (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) {
            URI uri = new URI(dbUrl);
            String userInfo = uri.getUserInfo();
            if (userInfo == null || userInfo.isBlank()) {
                throw new IllegalStateException("DATABASE_URL is missing username/password information");
            }

            String[] userParts = userInfo.split(":", 2);
            String username = URLDecoder.decode(userParts[0], StandardCharsets.UTF_8);
            String password = userParts.length > 1 ? URLDecoder.decode(userParts[1], StandardCharsets.UTF_8) : "";
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort() + uri.getPath();

            ds.setJdbcUrl(jdbcUrl);
            ds.setUsername(username);
            ds.setPassword(password);
        } else {
            // Fallback to explicit JDBC env vars
            String jdbc = env.getProperty("JDBC_DATABASE_URL");
            String jdbcUrl = (jdbc != null && !jdbc.isBlank()) ? jdbc : env.getProperty("spring.datasource.url");
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalStateException("Missing JDBC_DATABASE_URL or DATABASE_URL in prod profile");
            }

            ds.setJdbcUrl(jdbcUrl);
            ds.setUsername(env.getProperty("JDBC_DATABASE_USERNAME", env.getProperty("spring.datasource.username", "")));
            ds.setPassword(env.getProperty("JDBC_DATABASE_PASSWORD", env.getProperty("spring.datasource.password", "")));
        }

        ds.setMaximumPoolSize(Integer.parseInt(env.getProperty("spring.datasource.hikari.maximum-pool-size", "10")));
        ds.setMinimumIdle(Integer.parseInt(env.getProperty("spring.datasource.hikari.minimum-idle", "1")));

        return ds;
    }
}
