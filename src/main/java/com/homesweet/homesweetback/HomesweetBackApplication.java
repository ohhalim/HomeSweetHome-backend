package com.homesweet.homesweetback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;   // Scheduler드래곤

@SpringBootApplication
@EnableScheduling
public class HomesweetBackApplication {
    public static void main(String[] args) {
        SpringApplication.run(HomesweetBackApplication.class, args);
    }

}
