package com.parth.emergency_dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.parth.emergency_dashboard")
@EnableScheduling
public class EmergencyDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmergencyDashboardApplication.class, args);
    }
}