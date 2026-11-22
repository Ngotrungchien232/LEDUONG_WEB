package com.datalens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataLensApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataLensApplication.class, args);
    }
}
