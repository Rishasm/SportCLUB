package ru.sportclub.club;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import ru.sportclub.foundation.FoundationConfiguration;

@SpringBootApplication
@Import(FoundationConfiguration.class)
public class ClubApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClubApplication.class, args);
    }
}
