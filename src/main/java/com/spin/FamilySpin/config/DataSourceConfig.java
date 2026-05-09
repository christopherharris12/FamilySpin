package com.spin.FamilySpin.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;

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

        HikariDataSource ds = new HikariDataSource();

        if (dbUrl != null && dbUrl.startsWith("postgres://")) {
            URI uri = new URI(dbUrl);
            String[] userInfo = uri.getUserInfo().split(":");
            String username = userInfo[0];
            String password = userInfo.length > 1 ? userInfo[1] : "";
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort() + uri.getPath();

            ds.setJdbcUrl(jdbcUrl);
            ds.setUsername(username);
            ds.setPassword(password);
        } else {
            // Fallback to explicit JDBC env vars
            String jdbc = env.getProperty("JDBC_DATABASE_URL");
            ds.setJdbcUrl(jdbc != null ? jdbc : env.getProperty("spring.datasource.url"));
            ds.setUsername(env.getProperty("JDBC_DATABASE_USERNAME", env.getProperty("spring.datasource.username")));
            ds.setPassword(env.getProperty("JDBC_DATABASE_PASSWORD", env.getProperty("spring.datasource.password")));
        }

        ds.setMaximumPoolSize(Integer.parseInt(env.getProperty("spring.datasource.hikari.maximum-pool-size", "10")));
        ds.setMinimumIdle(Integer.parseInt(env.getProperty("spring.datasource.hikari.minimum-idle", "1")));

        return ds;
    }
}
