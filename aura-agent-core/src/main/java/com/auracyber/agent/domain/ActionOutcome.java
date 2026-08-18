package com.auracyber.agent.domain;

public sealed interface ActionOutcome {

	record Success(String detail) implements ActionOutcome {
	}

	/**
	 * @param falsePositive true when the action blocked legitimate traffic —
	 *                      triggers the CCE false-positive penalty on the acting agent.
	 */
	record Failure(String reason, boolean falsePositive) implements ActionOutcome {
	}
}
