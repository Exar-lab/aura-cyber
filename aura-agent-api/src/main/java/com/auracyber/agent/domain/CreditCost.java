package com.auracyber.agent.domain;

public record CreditCost(long tokens) {

	public static final CreditCost FREE = new CreditCost(0);

	public CreditCost {
		if (tokens < 0) {
			throw new IllegalArgumentException("CreditCost must not be negative");
		}
	}
}
