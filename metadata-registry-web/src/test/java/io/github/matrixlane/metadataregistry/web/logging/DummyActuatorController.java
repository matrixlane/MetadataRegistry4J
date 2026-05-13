package io.github.matrixlane.metadataregistry.web.logging;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Dummy controller that simulates actuator endpoints for security testing.
 * Used by {@link LoggingActuatorSecurityTest} to verify security rules.
 */
@Controller
class DummyActuatorController {

    @GetMapping("/actuator/health")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/actuator/info")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of("app", "test"));
    }

    @GetMapping("/actuator/loggers")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> loggers() {
        return ResponseEntity.ok(Map.of("loggers", Map.of()));
    }

    @PostMapping("/actuator/loggers/{name}")
    @ResponseBody
    public ResponseEntity<Void> configureLogger(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok().build();
    }
}
