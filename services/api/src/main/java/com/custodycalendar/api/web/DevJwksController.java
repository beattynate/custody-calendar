package com.custodycalendar.api.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DevJwksController {

    private final boolean enabled;
    private final Resource jwksResource;

    public DevJwksController(
            @Value("${app.security.dev-jwks.enabled:false}") boolean enabled,
            @Value("classpath:dev/jwks.json") Resource jwksResource) {
        this.enabled = enabled;
        this.jwksResource = jwksResource;
    }

    @GetMapping(value = "/dev/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks() {
        if (!enabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            String json = new String(jwksResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.ok(json);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JWKS not available");
        }
    }
}
