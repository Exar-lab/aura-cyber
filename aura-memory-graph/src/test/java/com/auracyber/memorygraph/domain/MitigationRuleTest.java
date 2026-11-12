package com.auracyber.memorygraph.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MitigationRuleTest {

	private static final Instant ASSESSED_AT = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	void acceptsConfidenceAtTheBoundsOfTheUnitInterval() {
		assertDoesNotThrow(() -> rule(0.0));
		assertDoesNotThrow(() -> rule(1.0));
	}

	@Test
	void rejectsConfidenceOutsideUnitInterval() {
		assertThrows(IllegalArgumentException.class, () -> rule(-0.01));
		assertThrows(IllegalArgumentException.class, () -> rule(1.01));
		assertThrows(IllegalArgumentException.class, () -> rule(Double.NaN));
	}

	private static MitigationRule rule(double confidence) {
		return new MitigationRule("rule-1", "T1110", "block source address", confidence, ASSESSED_AT, false);
	}
}
