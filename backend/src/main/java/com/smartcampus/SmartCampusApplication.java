package com.smartcampus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point of the Smart Campus backend.
 *
 * <p>Package layout follows the classic layered architecture that is expected
 * in the PBL submission:</p>
 *
 * <pre>
 *   controller  - REST endpoints (HTTP layer)
 *   service     - business logic, validation, booking synchronisation
 *   repository  - JDBC / SQL layer (PreparedStatement only)
 *   model       - entities, enums and reusable contracts (interfaces)
 *   dto         - request / response objects
 *   exception   - custom exceptions + global exception handler
 *   config      - CORS, interceptors, executors
 *   security    - login, tokens, roles
 * </pre>
 *
 * @author Smart Campus PBL
 */
@SpringBootApplication
public class SmartCampusApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartCampusApplication.class, args);
    }
}
