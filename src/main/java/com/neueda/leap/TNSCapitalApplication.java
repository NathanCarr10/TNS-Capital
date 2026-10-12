package com.neueda.leap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

/**
 * TNS Capital Trading API Application.
 * 
 * Architecture:
 * - Services → Repositories (Spring Data JPA/Hibernate) - for entity
 * persistence
 * - Validators → MyBatis Mappers (direct SQL) - for pre-validation speed
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
@MapperScan("com.neueda.leap.mappers")
public class TNSCapitalApplication {
    public static void main(String[] args) {
        SpringApplication.run(TNSCapitalApplication.class, args);
    }
}
