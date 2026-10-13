package com.example.productionlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ProductionLabServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductionLabServiceApplication.class, args);
    }
}
