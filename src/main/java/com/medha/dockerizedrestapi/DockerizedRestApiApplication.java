package com.medha.dockerizedrestapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Dockerized REST API demo.
 *
 * <p>This service exists to demonstrate Docker as a first-class delivery mechanism for a Spring
 * Boot application: a multi-stage {@code Dockerfile} builds a slim, non-root runtime image, and
 * the root {@code docker-compose.yml} wires this service together with a MySQL container and an
 * Adminer UI so the whole stack runs locally with a single {@code docker compose up}.
 */
@SpringBootApplication
public class DockerizedRestApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DockerizedRestApiApplication.class, args);
    }
}
