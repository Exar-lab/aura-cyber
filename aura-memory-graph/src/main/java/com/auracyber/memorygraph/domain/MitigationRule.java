package com.auracyber.memorygraph.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A countermeasure ("digital antibody") against a TTP. {@code confidence} is
 * the rule's strength in {@code [0, 1]} as of {@code assessedAt}; unconfirmed
 * rules lose strength from that instant onward per {@link SignatureDecay}.
 */
public record MitigationRule(
		String ruleId,
		String techniqueId,
		String countermeasure,
		double confidence,
		Instant assessedAt,
		boolean confirmed
) {

	public MitigationRule {
		Objects.requireNonNull(ruleId, "ruleId");
		Objects.requireNonNull(techniqueId, "techniqueId");
		Objects.requireNonNull(countermeasure, "countermeasure");
		Objects.requireNonNull(assessedAt, "assessedAt");
		if (!(confidence >= 0.0 && confidence <= 1.0)) {
			throw new IllegalArgumentException("confidence must be within [0, 1] but was " + confidence);
		}
	}
}
