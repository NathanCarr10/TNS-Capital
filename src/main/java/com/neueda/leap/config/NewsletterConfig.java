package com.neueda.leap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the newsletter.* settings, and has Hibernate write JSONB columns (the
 * newsletter's movers and news) with Spring's ObjectMapper, so dates are
 * stored as ISO-8601 text rather than numbers.
 */
@Configuration
@EnableConfigurationProperties(NewsletterProperties.class)
public class NewsletterConfig {

    @Bean
    HibernatePropertiesCustomizer jsonFormatMapper(ObjectMapper objectMapper) {
        return properties -> properties.put(AvailableSettings.JSON_FORMAT_MAPPER,
                new JacksonJsonFormatMapper(objectMapper));
    }
}
