package com.neueda.leap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

/**
 * TNS Capital Trading API Application.
 * 
 * Architecture:
 * - Services → Repositories (Spring Data JPA/Hibernate) - for entity persistence
 * - Validators → MyBatis Mappers (direct SQL) - for pre-validation speed
 */
@SpringBootApplication
@MapperScan("com.neueda.leap.mappers")
public class TNSCapitalApplication {
    public static void main(String[] args) {
        System.out.println("Hello world from TNS Capital's Sprint 2 project skeleton");
        System.out.println("Testing PR capabilities");
        SpringApplication.run(TNSCapitalApplication.class, args);
    }
}
