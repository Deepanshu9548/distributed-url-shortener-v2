package io.portfolio.urlshortener;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scopes Spring Boot's DEFAULT (shard-side) JPA stack to the shortener
 * package only, so the auth entities — which live in the control DB with
 * their own EMF/TxManager (ADR-010, {@code auth.ControlDbConfig}) — are not
 * picked up by the default EntityManagerFactory.
 *
 * <p>Lives in its own {@code @Configuration} (not on the application class)
 * so {@code @WebMvcTest} slices, which use the app class as context root,
 * don't try to bootstrap JPA repositories.
 */
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Map;

import org.springframework.context.annotation.Primary;

@Configuration
@EntityScan(basePackages = "io.portfolio.urlshortener.shortener")
@EnableJpaRepositories(basePackages = "io.portfolio.urlshortener.shortener")
public class ShardJpaConfig {

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("io.portfolio.urlshortener.shortener");
        emf.setPersistenceUnitName("shard");

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
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
