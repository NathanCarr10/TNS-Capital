package com.neueda.leap.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * MyBatis Configuration.
 * Provides SqlSessionFactory bean for MyBatis annotation-based mappers.
 */
@Configuration
public class MyBatisConfig {
    
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        
        // Register custom type handlers
        TypeHandler<UUID> uuidTypeHandler = new UUIDTypeHandler();
        bean.setTypeHandlers(new TypeHandler<?>[] { uuidTypeHandler });
        
        return bean.getObject();
    }
}
