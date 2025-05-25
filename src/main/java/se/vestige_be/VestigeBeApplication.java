package se.vestige_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VestigeBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(VestigeBeApplication.class, args);
    }

}
