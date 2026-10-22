package com.neueda.leap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.cfg.MappingSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the newsletter.* settings, and has Hibernate write JSONB columns (the
 * newsletter's movers) with Spring's ObjectMapper, so they are written with
 * the same settings as the API's responses.
 */
@Configuration
@EnableConfigurationProperties(NewsletterProperties.class)
public class NewsletterConfig {

    @Bean
    HibernatePropertiesCustomizer jsonFormatMapper(ObjectMapper objectMapper) {
        return properties -> properties.put(MappingSettings.JSON_FORMAT_MAPPER,
                new JacksonJsonFormatMapper(objectMapper));
    }
}
