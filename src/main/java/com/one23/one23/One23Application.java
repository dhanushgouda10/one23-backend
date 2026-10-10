package com.one23.one23;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableAsync: lets EmailService send the welcome email in the background
// @EnableScheduling: runs CleanupScheduler every minute
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class One23Application {

    public static void main(String[] args) {
        SpringApplication.run(One23Application.class, args);
    }
}
