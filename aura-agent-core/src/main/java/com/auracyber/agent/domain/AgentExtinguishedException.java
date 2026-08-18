package com.auracyber.agent.domain;

/**
 * Raised when a bankrupt agent is asked to run. Its policy is expected
 * to already have been revoked by the CCE ledger at bankruptcy time.
 */
public class AgentExtinguishedException extends RuntimeException {

	public AgentExtinguishedException(AgentId agentId) {
		super("Agent %s is bankrupt and its policy has been revoked".formatted(agentId.value()));
	}
}
