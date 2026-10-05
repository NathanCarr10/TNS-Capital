package com.neueda.leap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * TNS Capital Trading API Application.
 *
 * Architecture:
 * - Controllers → Services → Repositories (Spring Data JPA/Hibernate)
 * - The schema is owned by the db/ image; Hibernate only validates it
 * - Orders are executed asynchronously from the Kafka orders topic
 */
@SpringBootApplication
@EnableKafka
public class TNSCapitalApplication {
    public static void main(String[] args) {
        SpringApplication.run(TNSCapitalApplication.class, args);
    }
}
