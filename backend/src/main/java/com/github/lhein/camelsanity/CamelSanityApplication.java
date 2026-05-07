package com.github.lhein.camelsanity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CamelSanityApplication {
    public static void main(String[] args) {
        SpringApplication.run(CamelSanityApplication.class, args);
    }
}
