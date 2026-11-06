package ru.sportclub.members.config;

import ru.sportclub.members.service.MembersService;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sportclub.demo.enabled", havingValue = "true")
public class DemoMembersConfiguration {
    @Bean
    ApplicationRunner createDemoMember(MembersService members) {
        return args -> members.ensureDemoAccount();
    }
}
