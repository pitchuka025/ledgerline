package com.ledgerline.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);
    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(result)) {
                return ResponseEntity.ok(Map.of("status", "UP", "database", "UP",
                        "service", "LedgerLine", "timestamp", Instant.now()));
            }
        } catch (DataAccessException exception) {
            logger.warn("Database health check failed: {}", exception.getClass().getSimpleName());
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "DOWN", "database", "DOWN",
                        "service", "LedgerLine", "timestamp", Instant.now()));
    }
}
