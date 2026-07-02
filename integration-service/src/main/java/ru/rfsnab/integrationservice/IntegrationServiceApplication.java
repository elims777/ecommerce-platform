package ru.rfsnab.integrationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableScheduling
@EnableAsync
public class IntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationServiceApplication.class, args);
    }

}
