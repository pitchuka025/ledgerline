package com.ledgerline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LedgerLineApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerLineApplication.class, args);
    }
}
