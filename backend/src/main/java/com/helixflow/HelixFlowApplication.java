package com.helixflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HelixFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelixFlowApplication.class, args);
    }
}
