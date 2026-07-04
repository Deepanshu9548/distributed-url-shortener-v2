package io.portfolio.urlshortener.auth;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Control DB (ADR-010): OWN qualified DataSource + EntityManagerFactory +
 * TransactionManager, scoped to the {@code auth} entity package. Deliberately
 * NOT {@code @Primary} so the shard's {@link org.springframework.core.annotation.Order
 * @Primary} routing DataSource (Track B) stays the default for the shortener
 * side of the app.
 *
 * <p>Programmatic Flyway runs at bean-init time against the control datasource,
 * location {@code classpath:db/migration/control}. Same shape as
 * {@link io.portfolio.urlshortener.sharding.ShardDataSourceConfig}.
 *
 * <p>{@code @EnableJpaRepositories} is scoped to {@code io.portfolio.urlshortener.auth}
 * so the auth {@link UserRepository} and {@link LinkIndexRepository} bind to
 * this EMF/TxManager. Shortener repositories are registered separately from
 * the main application class.
 */
@Configuration
@ConditionalOnProperty(name = "app.control-db.jdbc-url")
@EnableJpaRepositories(
        basePackages = "io.portfolio.urlshortener.auth",
        entityManagerFactoryRef = "controlEntityManagerFactory",
        transactionManagerRef = "controlTransactionManager")
public class ControlDbConfig {

    static final String MIGRATION_LOCATION = "classpath:db/migration/control";
    static final long CONNECTION_TIMEOUT_MS = 1000;

    private static final Logger log = LoggerFactory.getLogger(ControlDbConfig.class);

    @Bean(destroyMethod = "close")
    public HikariDataSource controlDataSource(ControlDbProperties properties) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("control-db");
        ds.setJdbcUrl(properties.jdbcUrl());
        ds.setUsername(properties.username());
        ds.setPassword(properties.password());
        ds.setMaximumPoolSize(properties.poolSize());
        ds.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        // Fail-fast at boot: without users we can't authenticate anything.
        return ds;
    }

    @Bean
    public Flyway controlFlyway(DataSource controlDataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(controlDataSource)
                .locations(MIGRATION_LOCATION)
                .load();
        log.info("running control-db migrations");
        flyway.migrate();
        return flyway;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean controlEntityManagerFactory(
            DataSource controlDataSource, Flyway controlFlyway) {
        // Depend on Flyway so migrations run before validate() sees the schema.
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(controlDataSource);
        emf.setPackagesToScan("io.portfolio.urlshortener.auth");
        emf.setPersistenceUnitName("control");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(false);
        emf.setJpaVendorAdapter(adapter);

        emf.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "jakarta.persistence.query.timeout", "1000"
        ));
        return emf;
    }

    @Bean
    public PlatformTransactionManager controlTransactionManager(EntityManagerFactory controlEntityManagerFactory) {
        return new JpaTransactionManager(controlEntityManagerFactory);
    }
}
