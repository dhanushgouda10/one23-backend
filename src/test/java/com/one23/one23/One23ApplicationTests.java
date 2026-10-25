package com.one23.one23;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// Starts the whole application once to check that everything is wired correctly.
// It uses the LOCAL test database from src/test/resources/test-database.properties,
// never the production database from .env.
@SpringBootTest
@TestPropertySource(locations = "classpath:test-database.properties")
class One23ApplicationTests {

	@Test
	void contextLoads() {
	}

}
