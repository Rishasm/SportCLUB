package ru.sportclub.nutrition;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import ru.sportclub.foundation.FoundationConfiguration;

@SpringBootApplication
@Import(FoundationConfiguration.class)
public class NutritionApplication {
    public static void main(String[] args) {
        SpringApplication.run(NutritionApplication.class, args);
    }
}
