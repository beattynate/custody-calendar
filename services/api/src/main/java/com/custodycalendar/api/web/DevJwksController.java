package com.custodycalendar.api.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.core.io.ClassPathResource;

@RestController
@ConditionalOnProperty(name = "app.security.dev-jwks.enabled", havingValue = "true")
public class DevJwksController {

    private final Path jwksPath;

    public DevJwksController(
            @Value("${app.security.dev-jwks.path:${user.dir}/dev/jwt/jwks.json}") String jwksPath) {
        this.jwksPath = Paths.get(jwksPath);
    }

    @GetMapping(value = "/dev/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks() {
        try {
            String json;
            if (Files.exists(jwksPath)) {
                json = Files.readString(jwksPath, StandardCharsets.UTF_8);
            } else {
                var resource = new ClassPathResource("dev/jwks.json");
                json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
            return ResponseEntity.ok(json);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JWKS not available");
        }
    }
}
