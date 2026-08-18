package com.auracyber.agent.domain;

public class InsufficientCreditsException extends RuntimeException {

	public InsufficientCreditsException(AgentId agentId, CreditCost cost, long balance) {
		super("Agent %s cannot afford cost %d with balance %d".formatted(agentId.value(), cost.tokens(), balance));
	}
}
