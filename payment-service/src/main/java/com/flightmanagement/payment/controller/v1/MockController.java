package com.flightmanagement.payment.controller.v1;

import com.flightmanagement.payment.dto.MockConfiguration;
import com.flightmanagement.payment.service.MockConfigurationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for managing mock payment behavior.
 * Allows tests to configure payment outcomes at runtime.
 * 
 * WARNING: This endpoint should be disabled or secured in production.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/mock")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MockController {

    MockConfigurationService mockConfigService;

    @GetMapping("/config")
    public ResponseEntity<MockConfiguration> getConfiguration() {
        return ResponseEntity.ok(mockConfigService.getConfiguration());
    }

    @PutMapping("/config")
    public ResponseEntity<MockConfiguration> updateConfiguration(@RequestBody MockConfiguration config) {
        log.info("Updating mock configuration: {}", config);
        return ResponseEntity.ok(mockConfigService.updateConfiguration(config));
    }

    @PostMapping("/reset")
    public ResponseEntity<MockConfiguration> resetConfiguration() {
        log.info("Resetting mock configuration to defaults");
        return ResponseEntity.ok(mockConfigService.reset());
    }

    @PostMapping("/force-success")
    public ResponseEntity<Map<String, String>> forceSuccess() {
        mockConfigService.updateConfiguration(
                MockConfiguration.builder()
                        .forcedOutcome("SUCCESS")
                        .skipDelay(true)
                        .build()
        );
        return ResponseEntity.ok(Map.of(
                "status", "configured",
                "message", "All payments will now succeed instantly"
        ));
    }

    @PostMapping("/force-failure")
    public ResponseEntity<Map<String, String>> forceFailure() {
        mockConfigService.updateConfiguration(
                MockConfiguration.builder()
                        .forcedOutcome("FAILURE")
                        .skipDelay(true)
                        .build()
        );
        return ResponseEntity.ok(Map.of(
                "status", "configured",
                "message", "All payments will now fail instantly"
        ));
    }
}
