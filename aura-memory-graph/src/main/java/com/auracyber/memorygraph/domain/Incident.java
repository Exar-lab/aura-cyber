package com.auracyber.memorygraph.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * A security incident tying together the IoCs observed during it and the
 * TTPs the attacker exhibited.
 */
public record Incident(String incidentId, Instant observedAt, Set<Ioc> iocs, Set<Ttp> ttps) {

	public Incident {
		Objects.requireNonNull(incidentId, "incidentId");
		Objects.requireNonNull(observedAt, "observedAt");
		iocs = Set.copyOf(iocs);
		ttps = Set.copyOf(ttps);
	}
}
