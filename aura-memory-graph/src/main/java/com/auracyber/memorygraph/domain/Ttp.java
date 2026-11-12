package com.auracyber.memorygraph.domain;

import java.util.Objects;

/**
 * A MITRE ATT&CK technique, e.g. {@code T1110} (Brute Force) under the
 * {@code credential-access} tactic.
 */
public record Ttp(String techniqueId, String name, String tactic) {

	public Ttp {
		Objects.requireNonNull(techniqueId, "techniqueId");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(tactic, "tactic");
	}
}
