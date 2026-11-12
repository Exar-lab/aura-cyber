package com.auracyber.memorygraph.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureDecayTest {

	private static final double TOLERANCE = 1e-9;
	private static final Instant RAISED_AT = Instant.parse("2026-01-01T00:00:00Z");

	private final SignatureDecay decay = new SignatureDecay(Duration.ofHours(6), 0.25);

	@Test
	void confidenceIsUnchangedWhenNoTimeHasElapsed() {
		assertEquals(0.8, decay.decayedConfidence(0.8, Duration.ZERO), TOLERANCE);
	}

	@Test
	void confidenceHalvesAfterOneHalfLife() {
		assertEquals(0.4, decay.decayedConfidence(0.8, Duration.ofHours(6)), TOLERANCE);
	}

	@Test
	void confidenceQuartersAfterTwoHalfLives() {
		assertEquals(0.2, decay.decayedConfidence(0.8, Duration.ofHours(12)), TOLERANCE);
	}

	@Test
	void confidenceDecaysContinuouslyBetweenHalfLives() {
		assertEquals(0.8 / Math.sqrt(2), decay.decayedConfidence(0.8, Duration.ofHours(3)), TOLERANCE);
	}

	@Test
	void decayComposesAcrossConsecutiveIntervals() {
		double inOneStep = decay.decayedConfidence(1.0, Duration.ofHours(10));
		double inTwoSteps = decay.decayedConfidence(decay.decayedConfidence(1.0, Duration.ofHours(4)), Duration.ofHours(6));

		assertEquals(inOneStep, inTwoSteps, TOLERANCE);
	}

	@Test
	void negativeElapsedTimeDoesNotStrengthenConfidence() {
		assertEquals(0.8, decay.decayedConfidence(0.8, Duration.ofHours(-6)), TOLERANCE);
	}

	@Test
	void zeroConfidenceStaysZero() {
		assertEquals(0.0, decay.decayedConfidence(0.0, Duration.ofDays(30)), TOLERANCE);
	}

	@Test
	void assessDecaysUnconfirmedRuleAndStampsAssessmentTime() {
		Instant now = RAISED_AT.plus(Duration.ofHours(6));

		MitigationRule assessed = decay.assess(rule(0.8, false), now);

		assertEquals(0.4, assessed.confidence(), TOLERANCE);
		assertEquals(now, assessed.assessedAt());
		assertEquals("rule-1", assessed.ruleId());
		assertEquals("T1110", assessed.techniqueId());
		assertFalse(assessed.confirmed());
	}

	@Test
	void assessingAnAlreadyAssessedRuleDoesNotDecayItTwice() {
		Instant now = RAISED_AT.plus(Duration.ofHours(6));

		MitigationRule assessedTwice = decay.assess(decay.assess(rule(0.8, false), now), now);

		assertEquals(0.4, assessedTwice.confidence(), TOLERANCE);
	}

	@Test
	void assessLeavesConfirmedRuleUntouched() {
		MitigationRule confirmed = rule(0.8, true);

		assertSame(confirmed, decay.assess(confirmed, RAISED_AT.plus(Duration.ofDays(365))));
	}

	@Test
	void assessLeavesRuleUntouchedWhenNowPrecedesItsAssessment() {
		MitigationRule rule = rule(0.8, false);

		assertSame(rule, decay.assess(rule, RAISED_AT.minus(Duration.ofHours(1))));
	}

	@Test
	void ruleIsActiveWhileDecayedConfidenceIsAtOrAboveThreshold() {
		MitigationRule rule = rule(1.0, false);

		assertTrue(decay.isActive(rule, RAISED_AT));
		assertTrue(decay.isActive(rule, RAISED_AT.plus(Duration.ofHours(12))));
	}

	@Test
	void ruleStopsBeingActiveOnceDecayedBelowThreshold() {
		assertFalse(decay.isActive(rule(1.0, false), RAISED_AT.plus(Duration.ofHours(13))));
	}

	@Test
	void confirmedRuleStaysActiveRegardlessOfAge() {
		assertTrue(decay.isActive(rule(0.3, true), RAISED_AT.plus(Duration.ofDays(365))));
	}

	@Test
	void rejectsNonPositiveHalfLife() {
		assertThrows(IllegalArgumentException.class, () -> new SignatureDecay(Duration.ZERO, 0.25));
		assertThrows(IllegalArgumentException.class, () -> new SignatureDecay(Duration.ofHours(-1), 0.25));
	}

	@Test
	void rejectsThresholdOutsideUnitInterval() {
		assertThrows(IllegalArgumentException.class, () -> new SignatureDecay(Duration.ofHours(6), -0.1));
		assertThrows(IllegalArgumentException.class, () -> new SignatureDecay(Duration.ofHours(6), 1.1));
		assertThrows(IllegalArgumentException.class, () -> new SignatureDecay(Duration.ofHours(6), Double.NaN));
	}

	private static MitigationRule rule(double confidence, boolean confirmed) {
		return new MitigationRule("rule-1", "T1110", "block source address", confidence, RAISED_AT, confirmed);
	}
}
