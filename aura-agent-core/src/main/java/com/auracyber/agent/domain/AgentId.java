package com.auracyber.agent.domain;

public record AgentId(String value) {

	public AgentId {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("AgentId must not be blank");
		}
	}
}
