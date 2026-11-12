package com.auracyber.memorygraph.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Signature-decay policy of the Immune Memory Graph: an unconfirmed
 * mitigation rule loses strength exponentially,
 * {@code confidence(t) = confidence(0) * 0.5 ^ (t / halfLife)},
 * and stops being active once it falls below {@code activeThreshold}.
 * Confirmed rules never decay.
 */
public record SignatureDecay(Duration halfLife, double activeThreshold) {

	public SignatureDecay {
		Objects.requireNonNull(halfLife, "halfLife");
		if (halfLife.isZero() || halfLife.isNegative()) {
			throw new IllegalArgumentException("halfLife must be positive but was " + halfLife);
		}
		if (!(activeThreshold >= 0.0 && activeThreshold <= 1.0)) {
			throw new IllegalArgumentException("activeThreshold must be within [0, 1] but was " + activeThreshold);
		}
	}

	/** A non-positive {@code elapsed} leaves the confidence untouched. */
	public double decayedConfidence(double confidence, Duration elapsed) {
		if (elapsed.isZero() || elapsed.isNegative()) {
			return confidence;
		}
		double halfLives = (double) elapsed.toNanos() / halfLife.toNanos();
		return confidence * Math.pow(0.5, halfLives);
	}

	/** Re-assesses the rule at {@code now}, decaying its confidence unless it is confirmed. */
	public MitigationRule assess(MitigationRule rule, Instant now) {
		if (rule.confirmed() || !now.isAfter(rule.assessedAt())) {
			return rule;
		}
		double decayed = decayedConfidence(rule.confidence(), Duration.between(rule.assessedAt(), now));
		return new MitigationRule(
				rule.ruleId(), rule.techniqueId(), rule.countermeasure(), decayed, now, rule.confirmed()
		);
	}

	public boolean isActive(MitigationRule rule, Instant now) {
		return assess(rule, now).confidence() >= activeThreshold;
	}
}
